package ru.openide.reatom.gutter

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraphModel
import ru.openide.reatom.model.ReatomGraphNode
import ru.openide.reatom.navigation.ReatomNavigation
import ru.openide.reatom.navigation.ReatomNavigation.UsageFilter
import javax.swing.Icon

/**
 * Ставит нативные gutter-иконки на объявления Reatom-юнитов: отдельная иконка
 * для чтений и отдельная для записей. Клик по иконке чтения открывает только
 * чтения, по иконке записи — только записи. Иконки вешаются на markup-модель
 * редактора по offset'ам — `LineMarkerProvider` PSI-зависим, а TS-PSI нет.
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
            if (summary.readers > 0) {
                added += addIcon(
                    editor, start, end,
                    ReatomGutterIconRenderer(node, UsageFilter.READ, summary.readers),
                )
            }
            if (summary.writers > 0) {
                added += addIcon(
                    editor, start, end,
                    ReatomGutterIconRenderer(node, UsageFilter.WRITE, summary.writers),
                )
            }
        }
        editor.putUserData(HIGHLIGHTERS_KEY, added)
    }

    private fun addIcon(
        editor: Editor,
        start: Int,
        end: Int,
        renderer: GutterIconRenderer,
    ): RangeHighlighter {
        val highlighter = editor.markupModel.addRangeHighlighter(
            start,
            end,
            HighlighterLayer.ADDITIONAL_SYNTAX,
            null,
            HighlighterTargetArea.EXACT_RANGE,
        )
        highlighter.gutterIconRenderer = renderer
        return highlighter
    }

    private fun clear(editor: Editor) {
        val previous = editor.getUserData(HIGHLIGHTERS_KEY) ?: return
        for (highlighter in previous) {
            editor.markupModel.removeHighlighter(highlighter)
        }
        editor.putUserData(HIGHLIGHTERS_KEY, null)
    }
}

/**
 * Gutter-иконка одного вида связи (чтения или записи). По клику открывает
 * попап с использованиями только этого вида.
 */
private class ReatomGutterIconRenderer(
    private val node: ReatomGraphNode,
    private val filter: UsageFilter,
    private val count: Int,
) : GutterIconRenderer() {

    override fun getIcon(): Icon =
        if (filter == UsageFilter.WRITE) AllIcons.Gutter.WriteAccess
        else AllIcons.Gutter.ReadAccess

    override fun getTooltipText(): String {
        val arrow = if (filter == UsageFilter.WRITE) "↓" else "↑"
        return "Reatom ${node.kind} '${node.name}' · $arrow$count ${filter.noun}"
    }

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction =
        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                val editor = e.getData(CommonDataKeys.EDITOR) ?: return
                ReatomNavigation.showUsages(project, editor, node.id, filter)
            }
        }

    override fun equals(other: Any?): Boolean =
        other is ReatomGutterIconRenderer &&
            other.node.id == node.id &&
            other.filter == filter

    override fun hashCode(): Int = 31 * node.id.hashCode() + filter.hashCode()
}
