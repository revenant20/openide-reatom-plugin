/*
 * Copyright 2026 Fedor Sazonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fm.sazonov.reatom.analyzer

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import fm.sazonov.reatom.model.GraphRange
import fm.sazonov.reatom.model.ReatomGraph

/**
 * Проектный сервис: хранит модель реактивного графа и умеет её перестраивать,
 * запуская анализатор `@openide/reatom-ts-plugin` отдельным Node-процессом
 * (вариант 2a гибридной архитектуры). Code Lens и gutter-иконки читают
 * модель отсюда.
 *
 * При редактировании `.ts`/`.tsx`-файла offset'ы узлов в графе сразу
 * сдвигаются на дельту правки — Code Lens едет вместе с текстом, не дожидаясь
 * Node и без жёсткого сброса. Полный ре-анализ запускается в фоне после паузы:
 * он нужен лишь для структурных изменений (новые/удалённые юниты и связи).
 */
@Service(Service.Level.PROJECT)
class ReatomGraphService(private val project: Project) : Disposable {

    @Volatile
    var graph: ReatomGraph? = null
        private set

    private val lock = Any()
    private var loading = false
    private var pendingReload = false

    /** Дебаунсер ре-анализа: коалесцирует пачку правок в один запуск Node. */
    private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    /** Дебаунсер быстрого пересбора gutter-иконок после правок. */
    private val gutterAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val file = FileDocumentManager.getInstance().getFile(event.document)
                    if (file == null || !affectsGraph(file)) return
                    shiftGraph(file.path, event.offset, event.newLength - event.oldLength)
                    scheduleReload()
                    scheduleGutterRefresh()
                }
            },
            this,
        )
    }

    /**
     * Планирует ре-анализ после паузы в редактировании: сбрасывает прежний
     * запрос и ставит новый. По срабатыванию сохраняет документы на диск
     * (анализатор читает файлы с диска) и запускает Node.
     */
    fun scheduleReload() {
        reloadAlarm.cancelAllRequests()
        reloadAlarm.addRequest(
            {
                FileDocumentManager.getInstance().saveAllDocuments()
                reloadAsync()
            },
            RELOAD_DEBOUNCE_MS,
        )
    }

    /**
     * Планирует быстрый пересбор gutter-иконок после короткой паузы в
     * редактировании — отдельно от ре-анализа. Usage-иконки группируются по
     * строкам, а строки сдвигаются при любой правке: группировку надо
     * обновлять, даже когда граф структурно не менялся (и Node для этого не
     * нужен — `shiftGraph` уже поправил offset'ы в памяти).
     */
    fun scheduleGutterRefresh() {
        gutterAlarm.cancelAllRequests()
        gutterAlarm.addRequest(
            { ReatomGraphRefresher.refreshGutters(project) },
            GUTTER_DEBOUNCE_MS,
        )
    }

    /**
     * Перестраивает граф в фоне. Результат применяется, только если за время
     * анализа файлы не правились (иначе сдвинутый в памяти граф точнее —
     * `built` отдаётся следующему прогону) и он отличается от текущего;
     * по применению обновляет UI на EDT.
     */
    fun reloadAsync() {
        synchronized(lock) {
            if (loading) {
                pendingReload = true
                return
            }
            loading = true
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            var built: ReatomGraph? = null
            try {
                built = runAnalyzer()
            } finally {
                val rerun: Boolean
                val applied: Boolean
                synchronized(lock) {
                    loading = false
                    rerun = pendingReload
                    pendingReload = false
                    applied = built != null && !rerun && built != graph
                    if (applied) graph = built
                }
                if (applied) {
                    ApplicationManager.getApplication().invokeLater {
                        ReatomGraphRefresher.refreshAll(project)
                    }
                }
                if (rerun) reloadAsync()
            }
        }
    }

    /** Для тестов: задать модель напрямую, минуя запуск Node. */
    fun setGraphForTesting(value: ReatomGraph?) {
        graph = value
    }

    override fun dispose() = Unit

    /** Правка влияет на граф, если это `.ts`/`.tsx`-файл внутри проекта. */
    private fun affectsGraph(file: VirtualFile): Boolean {
        if (file.extension?.lowercase() !in TS_EXTENSIONS) return false
        val base = project.basePath ?: return false
        return file.path.startsWith(base)
    }

    /**
     * Сдвигает offset'ы узлов и рёбер файла `path` на `delta` после позиции
     * `at` — чтобы Code Lens сразу встал по месту, не дожидаясь ре-анализа.
     */
    private fun shiftGraph(path: String, at: Int, delta: Int) {
        if (delta == 0) return
        synchronized(lock) {
            val current = graph ?: return
            fun shifted(range: GraphRange): GraphRange =
                when {
                    range.start >= at -> GraphRange(range.start + delta, range.end + delta)
                    range.end > at -> GraphRange(range.start, range.end + delta)
                    else -> range
                }
            graph = current.copy(
                nodes = current.nodes.map {
                    if (it.file == path) it.copy(range = shifted(it.range)) else it
                },
                edges = current.edges.map {
                    if (it.file == path) it.copy(range = shifted(it.range)) else it
                },
            )
        }
    }

    private fun runAnalyzer(): ReatomGraph? {
        val locations = ReatomAnalyzerLocator.locate(project) ?: run {
            thisLogger().info(
                "Reatom: проект не использует Reatom либо не найдены node/CLI/tsconfig — " +
                    "граф не построен",
            )
            return null
        }
        return try {
            val commandLine = GeneralCommandLine(
                locations.node.path,
                locations.analyzerCli.path,
                "--project",
                locations.tsconfig.path,
            ).withWorkDirectory(locations.tsconfig.parent)
            val output = CapturingProcessHandler(commandLine).runProcess(ANALYZER_TIMEOUT_MS)
            if (output.exitCode != 0) {
                thisLogger().warn(
                    "Reatom: анализатор вернул код ${output.exitCode}: ${output.stderr}",
                )
                return null
            }
            Gson().fromJson(output.stdout, ReatomGraph::class.java)
        } catch (e: Exception) {
            thisLogger().warn("Reatom: не удалось построить граф", e)
            null
        }
    }

    companion object {
        private const val ANALYZER_TIMEOUT_MS = 60_000
        private const val RELOAD_DEBOUNCE_MS = 1_200
        private const val GUTTER_DEBOUNCE_MS = 250
        private val TS_EXTENSIONS = setOf("ts", "tsx", "mts", "cts")

        fun getInstance(project: Project): ReatomGraphService = project.service()
    }
}
