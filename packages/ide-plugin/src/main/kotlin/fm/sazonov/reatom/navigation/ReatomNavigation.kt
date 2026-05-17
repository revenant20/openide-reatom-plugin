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

package fm.sazonov.reatom.navigation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.RowIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import fm.sazonov.reatom.ReatomBundle
import fm.sazonov.reatom.analyzer.ReatomGraphService
import fm.sazonov.reatom.model.ReatomGraphEdge
import fm.sazonov.reatom.model.ReatomGraphModel
import fm.sazonov.reatom.model.ReatomGraphNode
import javax.swing.Icon
import javax.swing.JList

/**
 * Navigation over a unit's reactive relations — shared by Code Lens and gutter
 * icons. From a declaration — to the usages ([showUsages]); from a usage — to
 * the declaration ([showDeclarations]). A single target — navigate
 * immediately, several — via a list popup.
 */
object ReatomNavigation {

    /** Which usages to show: all, reads only, or writes only. */
    enum class UsageFilter(private val edgeKind: String?, private val key: String) {
        ALL(null, "usages"),
        READ("read", "readers"),
        WRITE("write", "writers"),
        ;

        /** Whether the edge matches the filter (`ALL` — any). */
        fun matches(kind: String): Boolean = edgeKind == null || edgeKind == kind

        /** The title of the usages popup of this kind. */
        fun title(name: String): String = ReatomBundle.message("navigation.title.$key", name)

        /** The hint shown when there are no usages of this kind. */
        fun emptyHint(name: String): String = ReatomBundle.message("navigation.empty.$key", name)
    }

    /** A usages popup entry — all usages on a single file line. */
    private class Usage(
        val file: String,
        val offset: Int,
        val location: String,
        val code: String,
        val kinds: Set<String>,
    ) {
        /** For type-to-search in the popup — both by location and by code. */
        override fun toString(): String = "$location $code"
    }

    /** Renders a usages popup row: relation-kind icon(s) + location + code. */
    private class UsageRenderer : ColoredListCellRenderer<Usage>() {
        override fun customizeCellRenderer(
            list: JList<out Usage>,
            value: Usage,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            icon = iconFor(value.kinds)
            append(value.location, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (value.code.isNotEmpty()) {
                append("  ")
                append(value.code, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    /** A declarations popup entry — one Reatom unit and its initialization site. */
    private class Declaration(val node: ReatomGraphNode, val location: String) {
        override fun toString(): String = "${node.name} ${node.kind} $location"
    }

    /** Renders a declarations popup row: the unit name + its role and location. */
    private class DeclarationRenderer : ColoredListCellRenderer<Declaration>() {
        override fun customizeCellRenderer(
            list: JList<out Declaration>,
            value: Declaration,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            icon = AllIcons.Gutter.OverridingMethod
            append(value.node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append("  ${value.node.kind}, ${value.location}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    /** The row icon: read, write, or both — if the line has both read and write. */
    private fun iconFor(kinds: Set<String>): Icon {
        val read = "read" in kinds
        val write = "write" in kinds
        return when {
            read && write -> RowIcon(AllIcons.Gutter.ReadAccess, AllIcons.Gutter.WriteAccess)
            write -> AllIcons.Gutter.WriteAccess
            else -> AllIcons.Gutter.ReadAccess
        }
    }

    /**
     * Shows the usages of unit `nodeId` according to `filter` and navigates to
     * the selected one. `ALL` — all relations (a Code Lens click),
     * `READ`/`WRITE` — reads / writes only (a click on the corresponding
     * gutter icon).
     */
    fun showUsages(project: Project, editor: Editor, nodeId: String, filter: UsageFilter) {
        val graph = ReatomGraphService.getInstance(project).graph
        val node = graph?.nodes?.find { it.id == nodeId }
        val name = node?.name ?: nodeId
        val edges = (if (graph != null) ReatomGraphModel.usagesOf(graph, nodeId) else emptyList())
            .filter { filter.matches(it.kind) }

        if (edges.isEmpty()) {
            HintManager.getInstance().showInformationHint(editor, filter.emptyHint(name))
            return
        }
        val usages = groupByLine(edges)
        if (usages.size == 1) {
            navigate(project, usages.first())
            return
        }
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(usages)
            .setTitle(filter.title(name))
            .setRenderer(UsageRenderer())
            .setFont(EditorUtil.getEditorFont())
            .setItemChosenCallback { navigate(project, it) }
            .createPopup()
        // Anchor the popup at the unit declaration — where the clicked gutter
        // icon / Code Lens is, not at the editor caret.
        val offset = node?.range?.start
        if (offset != null && offset <= editor.document.textLength) {
            val xy = editor.visualPositionToXY(editor.offsetToVisualPosition(offset))
            popup.show(RelativePoint(editor.contentComponent, xy))
        } else {
            popup.showInBestPositionFor(editor)
        }
    }

    /**
     * Navigates to a unit declaration (initialization) on a click on a usage
     * gutter icon. A single unit — immediately, several distinct ones on the
     * line — via a chooser popup.
     */
    fun showDeclarations(project: Project, editor: Editor, unitIds: List<String>) {
        val graph = ReatomGraphService.getInstance(project).graph ?: return
        val declarations = unitIds.distinct()
            .mapNotNull { id -> graph.nodes.find { it.id == id } }
            .map { Declaration(it, locationOf(it)) }
        if (declarations.isEmpty()) return
        if (declarations.size == 1) {
            navigateToDeclaration(project, declarations.first().node)
            return
        }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(declarations)
            .setTitle(ReatomBundle.message("navigation.declarations.title"))
            .setRenderer(DeclarationRenderer())
            .setFont(EditorUtil.getEditorFont())
            .setItemChosenCallback { navigateToDeclaration(project, it.node) }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    /** Opens the usage file and places the caret on the usage. */
    private fun navigate(project: Project, usage: Usage) {
        val file = LocalFileSystem.getInstance().findFileByPath(usage.file) ?: return
        OpenFileDescriptor(project, file, usage.offset).navigate(true)
    }

    /** Opens the declaration file and places the caret on the unit initialization. */
    private fun navigateToDeclaration(project: Project, node: ReatomGraphNode) {
        val file = LocalFileSystem.getInstance().findFileByPath(node.file) ?: return
        OpenFileDescriptor(project, file, node.range.start).navigate(true)
    }

    /** The location of a unit declaration as `file:line`. */
    private fun locationOf(node: ReatomGraphNode): String {
        val fileName = LocalFileSystem.getInstance().findFileByPath(node.file)?.name
            ?: node.file.substringAfterLast('/')
        val document = documentFor(node.file) ?: return fileName
        val offset = node.range.start.coerceIn(0, document.textLength)
        return "$fileName:${document.getLineNumber(offset) + 1}"
    }

    /**
     * Groups edges by file line: several usages on the same line yield a
     * single popup entry.
     */
    private fun groupByLine(edges: List<ReatomGraphEdge>): List<Usage> {
        val groups = LinkedHashMap<Pair<String, Int>, MutableList<ReatomGraphEdge>>()
        for (edge in edges) {
            val document = documentFor(edge.file)
            val line =
                if (document != null) {
                    document.getLineNumber(edge.range.start.coerceIn(0, document.textLength))
                } else {
                    -1
                }
            groups.getOrPut(edge.file to line) { ArrayList() }.add(edge)
        }
        return groups.map { (key, group) ->
            val (path, line) = key
            val document = documentFor(path)
            val fileName = LocalFileSystem.getInstance().findFileByPath(path)?.name
                ?: path.substringAfterLast('/')
            Usage(
                file = path,
                offset = group.minOf { it.range.start },
                location = if (line >= 0) "$fileName:${line + 1}" else fileName,
                code = if (document != null && line >= 0) lineText(document, line) else "",
                kinds = group.mapTo(HashSet()) { it.kind },
            )
        }
    }

    private fun documentFor(path: String): Document? {
        val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return null
        return FileDocumentManager.getInstance().getDocument(file)
    }

    /** The text of line `line` without indentation. */
    private fun lineText(document: Document, line: Int): String =
        document
            .getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            .trim()
}
