package ru.openide.reatom.analyzer

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import ru.openide.reatom.gutter.ReatomGutterRenderer

/** Обновляет UI после перестройки графа: gutter-иконки и проход подсветки. */
object ReatomGraphRefresher {

    /** Должно вызываться на EDT. */
    fun refreshAll(project: Project) {
        for (editor in openTextEditors(project)) {
            ReatomGutterRenderer.refresh(editor)
        }
        // Перезапуск демона перетрясёт проходы подсветки, включая Code Lens.
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    /** Открытые в проекте текстовые редакторы. */
    fun openTextEditors(project: Project): List<Editor> =
        FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<TextEditor>()
            .map { it.editor }
}
