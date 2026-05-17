package ru.openide.reatom.analyzer

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import ru.openide.reatom.codevision.ReatomCodeVisionProvider
import ru.openide.reatom.gutter.ReatomGutterRenderer

/** Обновляет UI после перестройки графа: gutter-иконки и Code Lens. */
object ReatomGraphRefresher {

    /** Обновляет gutter-иконки и Code Lens. Должно вызываться на EDT. */
    fun refreshAll(project: Project) {
        refreshGutters(project)
        // Code Lens кэшируется по провайдеру и НЕ обновляется перезапуском
        // демона подсветки — иначе после правки файла инлеи остаются на
        // прежних offset'ах и съезжают с объявлений. Объявляем провайдер
        // невалидным явно — платформа пересчитает `computeForEditor`.
        project.service<CodeVisionHost>().invalidateProvider(
            CodeVisionHost.LensInvalidateSignal(null, listOf(ReatomCodeVisionProvider.ID)),
        )
    }

    /**
     * Пересобирает только gutter-иконки — без сброса Code Lens. Вызывается
     * часто, после правок: usage-иконки группируются по строкам, а строки
     * сдвигаются при любом редактировании, даже когда структура графа та же
     * (разбил строку на три — иконок должно стать три). Должно вызываться на EDT.
     */
    fun refreshGutters(project: Project) {
        for (editor in openTextEditors(project)) {
            ReatomGutterRenderer.refresh(editor)
        }
    }

    /** Открытые в проекте текстовые редакторы. */
    fun openTextEditors(project: Project): List<Editor> =
        FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<TextEditor>()
            .map { it.editor }
}
