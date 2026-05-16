package ru.openide.reatom.codevision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
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
    override val name: String = "Reatom reactive graph"
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top

    override fun precomputeOnUiThread(editor: Editor) = Unit

    override fun computeForEditor(
        editor: Editor,
        uiData: Unit,
    ): List<Pair<TextRange, CodeVisionEntry>> {
        val project = editor.project ?: return emptyList()
        val filePath = FileDocumentManager.getInstance().getFile(editor.document)?.path
            ?: return emptyList()
        val graph = ReatomGraphService.getInstance(project).graph ?: return emptyList()

        val summaries = ReatomGraphModel.summarize(graph)
        val documentLength = editor.document.textLength
        val result = ArrayList<Pair<TextRange, CodeVisionEntry>>()
        for (node in ReatomGraphModel.nodesInFile(graph, filePath)) {
            val start = node.range.start
            val end = node.range.end
            if (start < 0 || end > documentLength || start >= end) continue
            val summary = summaries[node.id] ?: continue
            val text = ReatomGraphModel.lensText(summary)
            result += TextRange(start, end) to TextCodeVisionEntry(text, id)
        }
        return result
    }

    /** Клик по Code Lens — переход к использованиям юнита этой строки. */
    override fun handleClick(editor: Editor, textRange: TextRange, entry: CodeVisionEntry) {
        val project = editor.project ?: return
        val filePath = FileDocumentManager.getInstance().getFile(editor.document)?.path ?: return
        val graph = ReatomGraphService.getInstance(project).graph ?: return
        val node = graph.nodes.find {
            it.file == filePath && it.range.start == textRange.startOffset
        } ?: return
        ReatomNavigation.showUsages(project, editor, node.id)
    }

    companion object {
        const val ID: String = "reatom.reactiveGraph"
    }
}
