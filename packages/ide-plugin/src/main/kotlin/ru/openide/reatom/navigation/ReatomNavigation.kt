package ru.openide.reatom.navigation

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
import ru.openide.reatom.ReatomBundle
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraphEdge
import ru.openide.reatom.model.ReatomGraphModel
import javax.swing.Icon
import javax.swing.JList

/**
 * Навигация по реактивным связям юнита — общая для Code Lens и gutter-иконок.
 * Показывает использования юнита и переходит к выбранному: одно — сразу,
 * несколько — через попап-список.
 */
object ReatomNavigation {

    /** Какие использования показывать: все, только чтения или только записи. */
    enum class UsageFilter(private val edgeKind: String?, private val key: String) {
        ALL(null, "usages"),
        READ("read", "readers"),
        WRITE("write", "writers"),
        ;

        /** Подходит ли ребро под фильтр (`ALL` — любое). */
        fun matches(kind: String): Boolean = edgeKind == null || edgeKind == kind

        /** Заголовок попапа использований этого вида. */
        fun title(name: String): String = ReatomBundle.message("navigation.title.$key", name)

        /** Подсказка, когда использований этого вида нет. */
        fun emptyHint(name: String): String = ReatomBundle.message("navigation.empty.$key", name)
    }

    /** Запись попапа — все использования на одной строке файла. */
    private class Usage(
        val file: String,
        val offset: Int,
        val location: String,
        val code: String,
        val kinds: Set<String>,
    ) {
        /** Для type-to-search в попапе — и по расположению, и по коду. */
        override fun toString(): String = "$location $code"
    }

    /** Рендер строки попапа: иконка(и) видов связи + расположение + код. */
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

    /** Иконка строки: чтение, запись или обе — если на строке и read, и write. */
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
     * Показывает использования юнита `nodeId` с учётом `filter` и переходит к
     * выбранному. `ALL` — все связи (клик по Code Lens), `READ`/`WRITE` —
     * только чтения / записи (клик по соответствующей gutter-иконке).
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
        // Якорим попап у объявления юнита — там, где gutter-иконка / Code Lens,
        // по которым кликнули, а не у каретки редактора.
        val offset = node?.range?.start
        if (offset != null && offset <= editor.document.textLength) {
            val xy = editor.visualPositionToXY(editor.offsetToVisualPosition(offset))
            popup.show(RelativePoint(editor.contentComponent, xy))
        } else {
            popup.showInBestPositionFor(editor)
        }
    }

    /** Открывает файл записи и ставит курсор на использование. */
    private fun navigate(project: Project, usage: Usage) {
        val file = LocalFileSystem.getInstance().findFileByPath(usage.file) ?: return
        OpenFileDescriptor(project, file, usage.offset).navigate(true)
    }

    /**
     * Группирует рёбра по строке файла: несколько использований на одной
     * строке дают одну запись попапа.
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

    /** Текст строки `line` без отступов. */
    private fun lineText(document: Document, line: Int): String =
        document
            .getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            .trim()
}
