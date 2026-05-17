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

package fm.sazonov.reatom.analyzer

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import fm.sazonov.reatom.codevision.ReatomCodeVisionProvider
import fm.sazonov.reatom.gutter.ReatomGutterRenderer

/** Refreshes the UI after the graph is rebuilt: gutter icons and Code Lens. */
object ReatomGraphRefresher {

    /** Refreshes gutter icons and Code Lens. Must be called on the EDT. */
    fun refreshAll(project: Project) {
        refreshGutters(project)
        // Code Lens is cached per provider and is NOT updated by restarting the
        // highlighting daemon — otherwise, after a file edit, inlays stay at
        // their previous offsets and drift away from declarations. We invalidate
        // the provider explicitly — the platform recomputes `computeForEditor`.
        project.service<CodeVisionHost>().invalidateProvider(
            CodeVisionHost.LensInvalidateSignal(null, listOf(ReatomCodeVisionProvider.ID)),
        )
    }

    /**
     * Rebuilds only the gutter icons — without invalidating Code Lens. Called
     * frequently, after edits: usage icons are grouped by line, and lines shift
     * on any edit, even when the graph structure is the same (split a line into
     * three — there should now be three icons). Must be called on the EDT.
     */
    fun refreshGutters(project: Project) {
        for (editor in openTextEditors(project)) {
            ReatomGutterRenderer.refresh(editor)
        }
    }

    /** Text editors currently open in the project. */
    fun openTextEditors(project: Project): List<Editor> =
        FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<TextEditor>()
            .map { it.editor }
}
