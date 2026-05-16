package ru.openide.reatom

import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ru.openide.reatom.analyzer.ReatomGraphService
import ru.openide.reatom.codevision.ReatomCodeVisionProvider
import ru.openide.reatom.gutter.ReatomGutterRenderer
import ru.openide.reatom.model.GraphRange
import ru.openide.reatom.model.ReatomGraph
import ru.openide.reatom.model.ReatomGraphEdge
import ru.openide.reatom.model.ReatomGraphNode

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

        val entries = ReatomCodeVisionProvider().computeForEditor(myFixture.editor, Unit)
        assertEquals(1, entries.size)
        val (range, entry) = entries.first()
        assertEquals(start, range.startOffset)
        val lens = (entry as TextCodeVisionEntry).text
        assertTrue("в подписи есть роль atom: $lens", lens.contains("atom"))
        assertTrue("в подписи есть счётчики: $lens", lens.contains("↑1") && lens.contains("↓1"))
        assertTrue("в подписи есть расширение: $lens", lens.contains("withCache"))
    }

    fun testGutterIconAddedForAtom() {
        val text = "const counter = 0"
        myFixture.configureByText("model.ts", text)
        val path = myFixture.file.virtualFile.path
        val start = text.indexOf("counter")
        ReatomGraphService.getInstance(project)
            .setGraphForTesting(graphForCounter(path, start, start + "counter".length))

        ReatomGutterRenderer.refresh(myFixture.editor)
        val withGutter = myFixture.editor.markupModel.allHighlighters
            .count { it.gutterIconRenderer != null }
        assertEquals(1, withGutter)
    }

    fun testNoEntriesWhenGraphAbsent() {
        myFixture.configureByText("model.ts", "const counter = 0")
        ReatomGraphService.getInstance(project).setGraphForTesting(null)
        assertEmpty(ReatomCodeVisionProvider().computeForEditor(myFixture.editor, Unit))
    }
}
