package ru.openide.reatom.navigation

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.model.ReatomGraphEdge
import ru.openide.reatom.model.ReatomGraphModel

/**
 * Навигация по реактивным связям юнита — общая для Code Lens и gutter-иконок.
 * Показывает использования юнита (читателей и писателей) и переходит к
 * выбранному: одно использование — сразу, несколько — через попап-список.
 */
object ReatomNavigation {

    /** Элемент попап-списка; `toString` — то, что видно пользователю. */
    private class Usage(private val label: String, val edge: ReatomGraphEdge) {
        override fun toString(): String = label
    }

    /** Показывает использования юнита `nodeId` и переходит к выбранному. */
    fun showUsages(project: Project, editor: Editor, nodeId: String) {
        val graph = ReatomGraphService.getInstance(project).graph
        val name = graph?.nodes?.find { it.id == nodeId }?.name ?: nodeId
        val usages = if (graph != null) ReatomGraphModel.usagesOf(graph, nodeId) else emptyList()

        if (usages.isEmpty()) {
            HintManager.getInstance().showInformationHint(editor, "У «$name» нет использований")
            return
        }
        if (usages.size == 1) {
            navigate(project, usages.first())
            return
        }
        val items = usages.map { Usage(label(it), it) }
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(items)
            .setTitle("Использования «$name»")
            .setItemChosenCallback { navigate(project, it.edge) }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    /** Открывает файл ребра и ставит курсор на использование. */
    private fun navigate(project: Project, edge: ReatomGraphEdge) {
        val file = LocalFileSystem.getInstance().findFileByPath(edge.file) ?: return
        OpenFileDescriptor(project, file, edge.range.start).navigate(true)
    }

    /** Подпись элемента списка: `чтение · ui.ts:8`. */
    private fun label(edge: ReatomGraphEdge): String {
        val file = LocalFileSystem.getInstance().findFileByPath(edge.file)
        val fileName = file?.name ?: edge.file.substringAfterLast('/')
        val document = file?.let { FileDocumentManager.getInstance().getDocument(it) }
        val where =
            if (document != null) {
                val offset = edge.range.start.coerceIn(0, document.textLength)
                "$fileName:${document.getLineNumber(offset) + 1}"
            } else {
                fileName
            }
        val kind =
            when (edge.kind) {
                "read" -> "чтение"
                "write" -> "запись"
                "extend" -> "extend"
                else -> edge.kind
            }
        return "$kind · $where"
    }
}
