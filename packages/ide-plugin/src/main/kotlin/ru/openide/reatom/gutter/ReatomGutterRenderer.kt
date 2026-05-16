package ru.openide.reatom.gutter

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraphModel
import ru.openide.reatom.model.ReatomNodeSummary
import javax.swing.Icon

/**
 * Ставит нативные gutter-иконки на объявления Reatom-юнитов. Иконки вешаются
 * на markup-модель редактора по offset'ам из графа — `LineMarkerProvider` не
 * годится, он PSI-зависим, а TS-PSI в OpenIDE нет.
 */
object ReatomGutterRenderer {

    private val HIGHLIGHTERS_KEY: Key<MutableList<RangeHighlighter>> =
        Key.create("reatom.gutter.highlighters")

    /** Снимает прежние иконки редактора и ставит актуальные по модели графа. */
    fun refresh(editor: Editor) {
        clear(editor)
        val project = editor.project ?: return
        val filePath = FileDocumentManager.getInstance().getFile(editor.document)?.path ?: return
        val graph = ReatomGraphService.getInstance(project).graph ?: return

        val summaries = ReatomGraphModel.summarize(graph)
        val documentLength = editor.document.textLength
        val added = ArrayList<RangeHighlighter>()
        for (node in ReatomGraphModel.nodesInFile(graph, filePath)) {
            val start = node.range.start
            val end = node.range.end
            if (start < 0 || end > documentLength || start >= end) continue
            val summary = summaries[node.id] ?: continue
            val highlighter = editor.markupModel.addRangeHighlighter(
                start,
                end,
                HighlighterLayer.ADDITIONAL_SYNTAX,
                null,
                HighlighterTargetArea.EXACT_RANGE,
            )
            highlighter.gutterIconRenderer = ReatomGutterIconRenderer(summary)
            added += highlighter
        }
        editor.putUserData(HIGHLIGHTERS_KEY, added)
    }

    private fun clear(editor: Editor) {
        val previous = editor.getUserData(HIGHLIGHTERS_KEY) ?: return
        for (highlighter in previous) {
            editor.markupModel.removeHighlighter(highlighter)
        }
        editor.putUserData(HIGHLIGHTERS_KEY, null)
    }
}

/** Gutter-иконка с тултипом-сводкой реактивных связей юнита. */
private class ReatomGutterIconRenderer(
    private val summary: ReatomNodeSummary,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = AllIcons.Gutter.ReadAccess

    override fun getTooltipText(): String {
        val node = summary.node
        val extensions =
            if (node.extensions.isEmpty()) ""
            else " · расширения: " + node.extensions.joinToString(", ")
        return "Reatom ${node.kind} «${node.name}» · " +
            "↑${summary.readers} читателей · ↓${summary.writers} писателей" + extensions
    }

    override fun equals(other: Any?): Boolean =
        other is ReatomGutterIconRenderer && other.summary == summary

    override fun hashCode(): Int = summary.hashCode()
}
