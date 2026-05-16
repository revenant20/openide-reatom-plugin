package ru.openide.reatom.gutter

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import ru.openide.reatom.analyzer.ReatomGraphService

/**
 * На открытие файла: если граф ещё не построен — запускает анализатор
 * (по завершении он сам обновит все редакторы), иначе сразу ставит иконки.
 */
class ReatomFileEditorListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val service = ReatomGraphService.getInstance(source.project)
        if (service.graph == null) {
            service.reloadAsync()
            return
        }
        for (editor in source.getEditors(file).filterIsInstance<TextEditor>()) {
            ReatomGutterRenderer.refresh(editor.editor)
        }
    }
}
