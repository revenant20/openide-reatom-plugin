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

package fm.sazonov.reatom.codevision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import fm.sazonov.reatom.ReatomBundle
import fm.sazonov.reatom.analyzer.ReatomGraphService
import fm.sazonov.reatom.model.ReatomGraphModel
import fm.sazonov.reatom.navigation.ReatomNavigation

/**
 * Native IntelliJ-platform Code Lens: a clickable summary line above
 * `atom` / `computed` / `action` / `effect` declarations. Works on the offsets
 * of the graph model, without PSI (there is no TS-PSI in OpenIDE).
 *
 * `CodeVisionProvider` is `@ApiStatus.Experimental` — there is no stable
 * platform API for native Code Lens, so the unstable-API warning is suppressed.
 */
@Suppress("UnstableApiUsage")
class ReatomCodeVisionProvider : CodeVisionProvider<Unit> {

    override val id: String = ID
    override val name: String get() = ReatomBundle.message("codeVision.name")
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top

    override fun precomputeOnUiThread(editor: Editor) = Unit

    /**
     * Computes Code Lens entries from the graph model (on a background thread).
     * `computeForEditor` is declared deprecated/scheduled-for-removal by the
     * platform — we use the current `computeCodeVision`.
     */
    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        val project = editor.project ?: return CodeVisionState.READY_EMPTY
        val filePath = FileDocumentManager.getInstance().getFile(editor.document)?.path
            ?: return CodeVisionState.READY_EMPTY
        val graph = ReatomGraphService.getInstance(project).graph
            ?: return CodeVisionState.READY_EMPTY

        val summaries = ReatomGraphModel.summarize(graph)
        val documentLength = editor.document.textLength
        val result = ArrayList<Pair<TextRange, CodeVisionEntry>>()
        for (node in ReatomGraphModel.nodesInFile(graph, filePath)) {
            val start = node.range.start
            val end = node.range.end
            if (start < 0 || end > documentLength || start >= end) continue
            val summary = summaries[node.id] ?: continue
            val nodeId = node.id
            // ClickableTextCodeVisionEntry carries the click handler itself —
            // a separate handleClick is not needed. We do not capture PSI in
            // the handler.
            val entry = ClickableTextCodeVisionEntry(
                text = ReatomGraphModel.lensText(summary),
                providerId = ID,
                onClick = { _, clickEditor ->
                    clickEditor.project?.let { clickProject ->
                        ReatomNavigation.showUsages(
                            clickProject, clickEditor, nodeId, ReatomNavigation.UsageFilter.ALL,
                        )
                    }
                },
            )
            result += TextRange(start, end) to entry
        }
        return CodeVisionState.Ready(result)
    }

    companion object {
        const val ID: String = "reatom.reactive.graph"
    }
}
