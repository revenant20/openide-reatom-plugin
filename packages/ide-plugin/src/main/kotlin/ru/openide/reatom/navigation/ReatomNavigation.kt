package ru.openide.reatom.navigation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.awt.RelativePoint
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraphEdge
import ru.openide.reatom.model.ReatomGraphModel
import javax.swing.JList

/**
 * Навигация по реактивным связям юнита — общая для Code Lens и gutter-иконок.
 * Показывает использования юнита (читателей и писателей) и переходит к
 * выбранному: одно использование — сразу, несколько — через попап-список.
 */
object ReatomNavigation {

    /** Элемент попап-списка: расположение, код строки и ребро для перехода. */
    private class Usage(
        val location: String,
        val code: String,
        val edge: ReatomGraphEdge,
    ) {
        /** Для type-to-search в попапе — и по расположению, и по коду. */
        override fun toString(): String = "$location $code"
    }

    /** Рендер строки попапа: иконка чтения/записи + расположение + код строки. */
    private class UsageRenderer : ColoredListCellRenderer<Usage>() {
        override fun customizeCellRenderer(
            list: JList<out Usage>,
            value: Usage,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            icon =
                if (value.edge.kind == "write") AllIcons.Gutter.WriteAccess
                else AllIcons.Gutter.ReadAccess
            append(value.location, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (value.code.isNotEmpty()) {
                append("  ")
                append(value.code, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    /** Показывает использования юнита `nodeId` и переходит к выбранному. */
    fun showUsages(project: Project, editor: Editor, nodeId: String) {
        val graph = ReatomGraphService.getInstance(project).graph
        val node = graph?.nodes?.find { it.id == nodeId }
        val name = node?.name ?: nodeId
        val usages = if (graph != null) ReatomGraphModel.usagesOf(graph, nodeId) else emptyList()

        if (usages.isEmpty()) {
            HintManager.getInstance().showInformationHint(editor, "'$name' has no usages")
            return
        }
        if (usages.size == 1) {
            navigate(project, usages.first())
            return
        }
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(usages.map { usageOf(it) })
            .setTitle("Usages of '$name'")
            .setRenderer(UsageRenderer())
            .setItemChosenCallback { navigate(project, it.edge) }
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

    /** Открывает файл ребра и ставит курсор на использование. */
    private fun navigate(project: Project, edge: ReatomGraphEdge) {
        val file = LocalFileSystem.getInstance().findFileByPath(edge.file) ?: return
        OpenFileDescriptor(project, file, edge.range.start).navigate(true)
    }

    /** Собирает элемент списка: расположение `ui.ts:8` и код этой строки. */
    private fun usageOf(edge: ReatomGraphEdge): Usage {
        val file = LocalFileSystem.getInstance().findFileByPath(edge.file)
        val fileName = file?.name ?: edge.file.substringAfterLast('/')
        val document = file?.let { FileDocumentManager.getInstance().getDocument(it) }
            ?: return Usage(fileName, "", edge)
        val offset = edge.range.start.coerceIn(0, document.textLength)
        val line = document.getLineNumber(offset)
        return Usage("$fileName:${line + 1}", lineText(document, line), edge)
    }

    /** Текст строки `line` без отступов. */
    private fun lineText(document: Document, line: Int): String =
        document
            .getText(TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
            .trim()
}
