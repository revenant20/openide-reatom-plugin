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
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import fm.sazonov.reatom.model.GraphRange
import fm.sazonov.reatom.model.ReatomGraph

/**
 * Project service: holds the reactive graph model and can rebuild it by
 * running the `@openide/reatom-ts-plugin` analyzer in a separate Node process
 * (variant 2a of the hybrid architecture). Code Lens and gutter icons read the
 * model from here.
 *
 * When a `.ts`/`.tsx` file is edited, the offsets of the graph nodes are
 * immediately shifted by the edit delta — Code Lens moves together with the
 * text, without waiting for Node and without a hard invalidation. A full
 * re-analysis is scheduled in the background after a pause: it is only needed
 * for structural changes (added/removed units and links).
 */
@Service(Service.Level.PROJECT)
class ReatomGraphService(private val project: Project) : Disposable {

    @Volatile
    var graph: ReatomGraph? = null
        private set

    private val lock = Any()
    private var loading = false
    private var pendingReload = false

    /** Re-analysis debouncer: coalesces a batch of edits into one Node run. */
    private val reloadQueue =
        MergingUpdateQueue("reatom.reload", RELOAD_DEBOUNCE_MS, true, null, this)

    /** Debouncer for the quick rebuild of gutter icons after edits. */
    private val gutterQueue =
        MergingUpdateQueue("reatom.gutter", GUTTER_DEBOUNCE_MS, true, null, this)

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
     * Schedules a re-analysis after a pause in editing: repeated calls within
     * the debounce window coalesce into one run. When it fires, saves the
     * documents to disk (the analyzer reads files from disk) and launches Node.
     */
    fun scheduleReload() {
            reloadQueue.queue(
            Update.create(RELOAD_TASK) {
                FileDocumentManager.getInstance().saveAllDocuments()
                reloadAsync()
            }
        )
    }

    /**
     * Schedules a quick rebuild of gutter icons after a short pause in
     * editing — separately from the re-analysis. Usage icons are grouped by
     * line, and lines shift on any edit: the grouping must be refreshed even
     * when the graph has not changed structurally (and Node is not needed for
     * that — `shiftGraph` has already corrected the offsets in memory).
     */
    fun scheduleGutterRefresh() {
        gutterQueue.queue(
            Update.create(GUTTER_TASK) {
                ReatomGraphRefresher.refreshGutters(project)
            }
        )
    }

    /**
     * Rebuilds the graph in the background. The result is applied only if the
     * files were not edited during the analysis (otherwise the in-memory
     * shifted graph is more accurate — `built` is handed to the next run) and
     * it differs from the current one; once applied, it refreshes the UI on
     * the EDT.
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

    /** For tests: set the model directly, bypassing the Node launch. */
    fun setGraphForTesting(value: ReatomGraph?) {
        graph = value
    }

    override fun dispose() = Unit

    /** An edit affects the graph if it is a `.ts`/`.tsx` file inside the project. */
    private fun affectsGraph(file: VirtualFile): Boolean {
        if (file.extension?.lowercase() !in TS_EXTENSIONS) return false
        val base = project.basePath ?: return false
        return file.path.startsWith(base)
    }

    /**
     * Shifts the offsets of nodes and edges in file `path` by `delta` after
     * position `at` — so that Code Lens stays in place immediately, without
     * waiting for the re-analysis.
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
                "Reatom: the project does not use Reatom, or node/CLI/tsconfig were not " +
                    "found — graph not built",
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
                    "Reatom: analyzer returned code ${output.exitCode}: ${output.stderr}",
                )
                return null
            }
            Gson().fromJson(output.stdout, ReatomGraph::class.java)
        } catch (e: Exception) {
            thisLogger().warn("Reatom: failed to build the graph", e)
            null
        }
    }

    companion object {
        private const val ANALYZER_TIMEOUT_MS = 60_000
        private const val RELOAD_DEBOUNCE_MS = 1_200
        private const val GUTTER_DEBOUNCE_MS = 250

        /** Merge identities — re-queuing the same identity coalesces updates. */
        private const val RELOAD_TASK = "reload"
        private const val GUTTER_TASK = "gutter"

        private val TS_EXTENSIONS = setOf("ts", "tsx", "mts", "cts")

        fun getInstance(project: Project): ReatomGraphService = project.service()
    }
}
