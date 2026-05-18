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

package fm.sazonov.reatom.gutter

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
import fm.sazonov.reatom.ReatomBundle
import fm.sazonov.reatom.analyzer.ReatomGraphService
import fm.sazonov.reatom.model.ReatomGraph
import fm.sazonov.reatom.model.ReatomGraphEdge
import fm.sazonov.reatom.model.ReatomGraphModel
import fm.sazonov.reatom.model.ReatomGraphNode
import fm.sazonov.reatom.navigation.ReatomNavigation
import fm.sazonov.reatom.navigation.ReatomNavigation.UsageFilter
import javax.swing.Icon

/**
 * Places native gutter icons on the lines of Reatom units in the editor. On
 * declarations — separate icons for reads and writes (a click leads to the
 * usages). On usage lines — an icon for navigating to the unit declaration (a
 * click leads to the initialization site, including in another file). The
 * icons are attached to the markup model by offsets — `LineMarkerProvider` is
 * PSI-dependent, and there is no TS-PSI.
 */
object ReatomGutterRenderer {

    private val HIGHLIGHTERS_KEY: Key<MutableList<RangeHighlighter>> =
        Key.create("reatom.gutter.highlighters")

    /** Removes the editor's previous icons and places current ones from the graph model. */
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

    /** Read/write icons on unit declarations. */
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
     * Navigate-to-declaration icons on unit usage lines. Graph edges are
     * grouped by line: one line — one icon, its target being the declarations
     * of all units used on that line.
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
            if (start !in 0 until documentLength) continue
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
 * A gutter icon on a unit declaration — for a single relation kind (reads or
 * writes). On click, opens a popup with usages of that kind only.
 */
private class ReatomGutterIconRenderer(
    private val node: ReatomGraphNode,
    private val filter: UsageFilter,
    private val count: Int,
) : GutterIconRenderer() {

    override fun getIcon(): Icon =
        if (filter == UsageFilter.WRITE) {
            AllIcons.Gutter.WriteAccess
        } else {
            AllIcons.Gutter.ReadAccess
        }

    /** Declaration markers are aligned to the left edge of the gutter. */
    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getTooltipText(): String =
        ReatomBundle.message(
            if (filter == UsageFilter.WRITE) "gutter.tooltip.write" else "gutter.tooltip.read",
            node.kind,
            node.name,
            count,
        )

    /** The icon name for screen readers. */
    override fun getAccessibleName(): String =
        ReatomBundle.message(
            if (filter == UsageFilter.WRITE) {
                "gutter.accessibleName.write"
            } else {
                "gutter.accessibleName.read"
            },
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
 * A gutter icon on a line where unit(s) are used. On click, navigates to the
 * declaration: a single unit — immediately, several — via a chooser popup.
 */
private class ReatomUsageGutterIconRenderer(
    private val targets: List<ReatomGraphNode>,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = AllIcons.Gutter.OverridingMethod

    /** Markers are aligned to the left edge of the gutter. */
    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun getTooltipText(): String =
        if (targets.size == 1) {
            ReatomBundle.message(
                "gutter.usage.tooltip.single",
                targets[0].kind,
                targets[0].name,
            )
        } else {
            ReatomBundle.message("gutter.usage.tooltip.many", targets.size)
        }

    /** The icon name for screen readers. */
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
