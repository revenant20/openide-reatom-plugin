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
