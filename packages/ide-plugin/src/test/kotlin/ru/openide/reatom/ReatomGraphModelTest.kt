package ru.openide.reatom

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.openide.reatom.model.GraphRange
import ru.openide.reatom.model.ReatomGraph
import ru.openide.reatom.model.ReatomGraphEdge
import ru.openide.reatom.model.ReatomGraphModel
import ru.openide.reatom.model.ReatomGraphNode
import ru.openide.reatom.model.ReatomNodeSummary

/** Юнит-тесты чистых операций над моделью графа — без платформы. */
class ReatomGraphModelTest {

    private fun node(
        id: String,
        kind: String,
        file: String,
        extensions: List<String> = emptyList(),
    ) = ReatomGraphNode(
        id = id,
        kind = kind,
        name = id.substringAfterLast(':'),
        file = file,
        range = GraphRange(0, 5),
        extensions = extensions,
    )

    @Test
    fun summarizeCountsReadersAndWriters() {
        val graph = ReatomGraph(
            schemaVersion = 1,
            nodes = listOf(node("a.ts:counter", "atom", "a.ts")),
            edges = listOf(
                ReatomGraphEdge(to = "a.ts:counter", kind = "read", file = "b.ts"),
                ReatomGraphEdge(to = "a.ts:counter", kind = "read", file = "b.ts"),
                ReatomGraphEdge(to = "a.ts:counter", kind = "write", file = "b.ts"),
            ),
        )
        val summary = ReatomGraphModel.summarize(graph).getValue("a.ts:counter")
        assertEquals(2, summary.readers)
        assertEquals(1, summary.writers)
    }

    @Test
    fun lensTextIncludesRoleCountsAndExtensions() {
        val summary = ReatomNodeSummary(
            node = node("a.ts:data", "atom", "a.ts", listOf("withCache")),
            readers = 4,
            writers = 2,
        )
        assertEquals("atom · ↑4 · ↓2 · ⤴withCache", ReatomGraphModel.lensText(summary))
    }

    @Test
    fun nodesInFileFiltersByPath() {
        val graph = ReatomGraph(
            nodes = listOf(
                node("a.ts:x", "atom", "a.ts"),
                node("b.ts:y", "computed", "b.ts"),
            ),
        )
        val inA = ReatomGraphModel.nodesInFile(graph, "a.ts")
        assertEquals(1, inA.size)
        assertEquals("x", inA.first().name)
    }

    @Test
    fun usagesOfReturnsIncomingEdges() {
        val graph = ReatomGraph(
            nodes = listOf(node("a.ts:counter", "atom", "a.ts")),
            edges = listOf(
                ReatomGraphEdge(to = "a.ts:counter", kind = "read", file = "b.ts"),
                ReatomGraphEdge(to = "a.ts:counter", kind = "write", file = "b.ts"),
                ReatomGraphEdge(to = "a.ts:other", kind = "read", file = "b.ts"),
            ),
        )
        assertEquals(2, ReatomGraphModel.usagesOf(graph, "a.ts:counter").size)
    }
}
