package ru.openide.reatom.analyzer

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import ru.openide.reatom.model.ReatomGraph

/**
 * Проектный сервис: хранит модель реактивного графа и умеет её перестраивать,
 * запуская анализатор `@openide/reatom-ts-plugin` отдельным Node-процессом
 * (вариант 2a гибридной архитектуры). Code Lens и gutter-иконки читают
 * модель отсюда.
 *
 * Граф перестраивается при открытии проекта и после паузы в редактировании
 * любого `.ts`/`.tsx`-файла проекта: иначе offset'ы узлов устаревают и
 * Code Lens съезжает с объявлений (gutter-иконки на `RangeHighlighter`
 * двигаются вместе с текстом, а Code Lens пересчитывается со старых offset'ов).
 */
@Service(Service.Level.PROJECT)
class ReatomGraphService(private val project: Project) : Disposable {

    @Volatile
    var graph: ReatomGraph? = null
        private set

    private val lock = Any()
    private var loading = false
    private var pendingReload = false

    /** Дебаунсер перестроек: коалесцирует пачку правок в один запуск Node. */
    private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    if (affectsGraph(event.document)) scheduleReload()
                }
            },
            this,
        )
    }

    /**
     * Планирует перестройку графа после паузы в редактировании: сбрасывает
     * прежний запрос и ставит новый. По срабатыванию сохраняет документы на
     * диск (анализатор читает файлы с диска) и запускает Node.
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

    /** Перестраивает граф в фоне; по завершении обновляет UI на EDT. */
    fun reloadAsync() {
        synchronized(lock) {
            if (loading) {
                // Перестройка уже идёт — догоним её одним повтором по завершении.
                pendingReload = true
                return
            }
            loading = true
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val built = runAnalyzer()
                if (built != null) {
                    graph = built
                    ApplicationManager.getApplication().invokeLater {
                        ReatomGraphRefresher.refreshAll(project)
                    }
                }
            } finally {
                val rerun = synchronized(lock) {
                    loading = false
                    pendingReload.also { pendingReload = false }
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
    private fun affectsGraph(document: Document): Boolean {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return false
        if (file.extension?.lowercase() !in TS_EXTENSIONS) return false
        val base = project.basePath ?: return false
        return file.path.startsWith(base)
    }

    private fun runAnalyzer(): ReatomGraph? {
        val locations = ReatomAnalyzerLocator.locate(project) ?: run {
            thisLogger().info("Reatom: анализатор или tsconfig не найдены — граф не построен")
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
        private val TS_EXTENSIONS = setOf("ts", "tsx", "mts", "cts")

        fun getInstance(project: Project): ReatomGraphService = project.service()
    }
}
