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
import ru.openide.reatom.ReatomBundle
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraph
import ru.openide.reatom.model.ReatomGraphEdge
import ru.openide.reatom.model.ReatomGraphModel
import ru.openide.reatom.model.ReatomGraphNode
import ru.openide.reatom.navigation.ReatomNavigation
import ru.openide.reatom.navigation.ReatomNavigation.UsageFilter
import javax.swing.Icon

/**
 * Ставит нативные gutter-иконки на строки Reatom-юнитов в редакторе. На
 * объявлениях — отдельные иконки чтений и записей (клик ведёт к использованиям).
 * На строках использования — иконка перехода к объявлению юнита (клик ведёт
 * к месту инициализации, в т.ч. в другом файле). Иконки вешаются на
 * markup-модель по offset'ам — `LineMarkerProvider` PSI-зависим, а TS-PSI нет.
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

        val documentLength = editor.document.textLength
        val added = ArrayList<RangeHighlighter>()
        addDeclarationIcons(editor, graph, filePath, documentLength, added)
        addUsageIcons(editor, graph, filePath, documentLength, added)
        editor.putUserData(HIGHLIGHTERS_KEY, added)
    }

    /** Иконки чтений/записей на объявлениях юнитов. */
    private fun addDeclarationIcons(
        editor: Editor,
        graph: ReatomGraph,
        filePath: String,
        documentLength: Int,
        added: MutableList<RangeHighlighter>,
    ) {
        val summaries = ReatomGraphModel.summarize(graph)
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
    }

    /**
     * Иконки перехода к объявлению на строках использования юнитов. Рёбра
     * графа группируются по строке: одна строка — одна иконка, её цель —
     * объявления всех используемых на ней юнитов.
     */
    private fun addUsageIcons(
        editor: Editor,
        graph: ReatomGraph,
        filePath: String,
        documentLength: Int,
        added: MutableList<RangeHighlighter>,
    ) {
        val nodesById = graph.nodes.associateBy { it.id }
        val byLine = LinkedHashMap<Int, MutableList<ReatomGraphEdge>>()
        for (edge in graph.edges) {
            if (edge.file != filePath || edge.to !in nodesById) continue
            val start = edge.range.start
            if (start < 0 || start >= documentLength) continue
            byLine.getOrPut(editor.document.getLineNumber(start)) { ArrayList() }.add(edge)
        }
        for (edges in byLine.values) {
            val rep = edges.minByOrNull { it.range.start } ?: continue
            val end = rep.range.end.coerceAtMost(documentLength)
            if (rep.range.start >= end) continue
            val targets = edges.map { it.to }.distinct().mapNotNull { nodesById[it] }
            if (targets.isEmpty()) continue
            added += addIcon(editor, rep.range.start, end, ReatomUsageGutterIconRenderer(targets))
        }
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
 * Gutter-иконка на объявлении юнита — одного вида связи (чтения или записи).
 * По клику открывает попап с использованиями только этого вида.
 */
private class ReatomGutterIconRenderer(
    private val node: ReatomGraphNode,
    private val filter: UsageFilter,
    private val count: Int,
) : GutterIconRenderer() {

    override fun getIcon(): Icon =
        if (filter == UsageFilter.WRITE) AllIcons.Gutter.WriteAccess
        else AllIcons.Gutter.ReadAccess

    /** Маркеры объявлений выравниваются по левому краю gutter'а. */
    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getTooltipText(): String =
        ReatomBundle.message(
            if (filter == UsageFilter.WRITE) "gutter.tooltip.write" else "gutter.tooltip.read",
            node.kind,
            node.name,
            count,
        )

    /** Имя иконки для скринридеров. */
    override fun getAccessibleName(): String =
        ReatomBundle.message(
            if (filter == UsageFilter.WRITE) "gutter.accessibleName.write"
            else "gutter.accessibleName.read",
            node.kind,
            node.name,
        )

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

/**
 * Gutter-иконка на строке использования юнита(ов). По клику переходит к
 * объявлению: один юнит — сразу, несколько — через попап выбора.
 */
private class ReatomUsageGutterIconRenderer(
    private val targets: List<ReatomGraphNode>,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = AllIcons.Gutter.OverridingMethod

    /** Маркеры выравниваются по левому краю gutter'а. */
    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getTooltipText(): String =
        if (targets.size == 1) {
            ReatomBundle.message(
                "gutter.usage.tooltip.single", targets[0].kind, targets[0].name,
            )
        } else {
            ReatomBundle.message("gutter.usage.tooltip.many", targets.size)
        }

    /** Имя иконки для скринридеров. */
    override fun getAccessibleName(): String =
        ReatomBundle.message("gutter.usage.accessibleName")

    override fun isNavigateAction(): Boolean = true

    override fun getClickAction(): AnAction =
        object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                val project = e.project ?: return
                val editor = e.getData(CommonDataKeys.EDITOR) ?: return
                ReatomNavigation.showDeclarations(project, editor, targets.map { it.id })
            }
        }

    override fun equals(other: Any?): Boolean =
        other is ReatomUsageGutterIconRenderer &&
            other.targets.map { it.id } == targets.map { it.id }

    override fun hashCode(): Int = targets.map { it.id }.hashCode()
}
