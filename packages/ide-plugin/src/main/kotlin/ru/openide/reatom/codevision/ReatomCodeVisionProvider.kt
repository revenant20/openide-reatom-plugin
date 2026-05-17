package ru.openide.reatom.codevision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import ru.openide.reatom.ReatomBundle
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraphModel
import ru.openide.reatom.navigation.ReatomNavigation

/**
 * Нативный Code Lens платформы IntelliJ: кликабельная строка-сводка над
 * объявлениями `atom` / `computed` / `action` / `effect`. Работает по
 * offset'ам модели графа, без PSI (TS-PSI в OpenIDE нет).
 */
class ReatomCodeVisionProvider : CodeVisionProvider<Unit> {

    override val id: String = ID
    override val name: String get() = ReatomBundle.message("codeVision.name")
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top

    override fun precomputeOnUiThread(editor: Editor) = Unit

    /**
     * Считает Code Lens-записи по модели графа (на фоновом потоке).
     * `computeForEditor` платформой объявлен deprecated/scheduled-for-removal —
     * используем актуальный `computeCodeVision`.
     */
    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        val project = editor.project ?: return CodeVisionState.READY_EMPTY
        val filePath = FileDocumentManager.getInstance().getFile(editor.document)?.path
            ?: return CodeVisionState.READY_EMPTY
        val graph = ReatomGraphService.getInstance(project).graph
            ?: return CodeVisionState.READY_EMPTY

        val summaries = ReatomGraphModel.summarize(graph)
        val documentLength = editor.document.textLength
        val result = ArrayList<Pair<TextRange, CodeVisionEntry>>()
        for (node in ReatomGraphModel.nodesInFile(graph, filePath)) {
            val start = node.range.start
            val end = node.range.end
            if (start < 0 || end > documentLength || start >= end) continue
            val summary = summaries[node.id] ?: continue
            val nodeId = node.id
            // ClickableTextCodeVisionEntry несёт обработчик клика сам —
            // отдельный handleClick не нужен. PSI в обработчик не захватываем.
            val entry = ClickableTextCodeVisionEntry(
                text = ReatomGraphModel.lensText(summary),
                providerId = ID,
                onClick = { _, clickEditor ->
                    clickEditor.project?.let { clickProject ->
                        ReatomNavigation.showUsages(
                            clickProject, clickEditor, nodeId, ReatomNavigation.UsageFilter.ALL,
                        )
                    }
                },
            )
            result += TextRange(start, end) to entry
        }
        return CodeVisionState.Ready(result)
    }

    companion object {
        const val ID: String = "reatom.reactive.graph"
    }
}
