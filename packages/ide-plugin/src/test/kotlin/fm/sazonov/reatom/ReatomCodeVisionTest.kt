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

package fm.sazonov.reatom

import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import fm.sazonov.reatom.analyzer.ReatomGraphService
import fm.sazonov.reatom.codevision.ReatomCodeVisionProvider
import fm.sazonov.reatom.gutter.ReatomGutterRenderer
import fm.sazonov.reatom.model.GraphRange
import fm.sazonov.reatom.model.ReatomGraph
import fm.sazonov.reatom.model.ReatomGraphEdge
import fm.sazonov.reatom.model.ReatomGraphNode

/** Тесты рендеринга Code Lens и gutter-иконок по заданной модели графа. */
class ReatomCodeVisionTest : BasePlatformTestCase() {

    private fun graphForCounter(filePath: String, start: Int, end: Int): ReatomGraph {
        val id = "$filePath:counter"
        return ReatomGraph(
            schemaVersion = 1,
            nodes = listOf(
                ReatomGraphNode(
                    id = id,
                    kind = "atom",
                    name = "counter",
                    file = filePath,
                    range = GraphRange(start, end),
                    extensions = listOf("withCache"),
                ),
            ),
            edges = listOf(
                ReatomGraphEdge(to = id, kind = "read", file = filePath),
                ReatomGraphEdge(to = id, kind = "write", file = filePath),
            ),
        )
    }

    fun testCodeVisionEntryForAtomDeclaration() {
        val text = "const counter = 0"
        myFixture.configureByText("model.ts", text)
        val path = myFixture.file.virtualFile.path
        val start = text.indexOf("counter")
        ReatomGraphService.getInstance(project)
            .setGraphForTesting(graphForCounter(path, start, start + "counter".length))

        val entries = ReatomCodeVisionProvider().computeCodeVision(myFixture.editor, Unit).result
        assertEquals(1, entries.size)
        val (range, entry) = entries.first()
        assertEquals(start, range.startOffset)
        val lens = (entry as TextCodeVisionEntry).text
        assertTrue("в подписи есть роль atom: $lens", lens.contains("atom"))
        assertTrue("в подписи есть счётчики: $lens", lens.contains("↑1") && lens.contains("↓1"))
        assertTrue("в подписи есть расширение: $lens", lens.contains("withCache"))
    }

    fun testGutterIconsForReadAndWrite() {
        val text = "const counter = 0"
        myFixture.configureByText("model.ts", text)
        val path = myFixture.file.virtualFile.path
        val start = text.indexOf("counter")
        ReatomGraphService.getInstance(project)
            .setGraphForTesting(graphForCounter(path, start, start + "counter".length))

        ReatomGutterRenderer.refresh(myFixture.editor)
        val icons = myFixture.editor.markupModel.allHighlighters
            .mapNotNull { it.gutterIconRenderer?.icon }
        // у counter есть и чтение, и запись — две разные gutter-иконки
        assertEquals(2, icons.size)
        assertEquals(2, icons.toSet().size)
    }

    fun testUsageGutterIconForReference() {
        val text = "const counter = 0\nconst alias = counter"
        myFixture.configureByText("model.ts", text)
        val path = myFixture.file.virtualFile.path
        val declStart = text.indexOf("counter")
        val useStart = text.lastIndexOf("counter")
        val id = "$path:counter"
        ReatomGraphService.getInstance(project).setGraphForTesting(
            ReatomGraph(
                schemaVersion = 1,
                nodes = listOf(
                    ReatomGraphNode(
                        id = id, kind = "atom", name = "counter", file = path,
                        range = GraphRange(declStart, declStart + "counter".length),
                    ),
                ),
                edges = listOf(
                    ReatomGraphEdge(
                        to = id, kind = "read", file = path,
                        range = GraphRange(useStart, useStart + "counter".length),
                    ),
                ),
            ),
        )

        ReatomGutterRenderer.refresh(myFixture.editor)
        val icons = myFixture.editor.markupModel.allHighlighters
            .mapNotNull { it.gutterIconRenderer?.icon }
        // на строке использования — иконка перехода к объявлению
        assertTrue(
            "ожидалась usage-иконка перехода к объявлению",
            icons.contains(AllIcons.Gutter.OverridingMethod),
        )
    }

    fun testNoEntriesWhenGraphAbsent() {
        myFixture.configureByText("model.ts", "const counter = 0")
        ReatomGraphService.getInstance(project).setGraphForTesting(null)
        assertEmpty(ReatomCodeVisionProvider().computeCodeVision(myFixture.editor, Unit).result)
    }
}
