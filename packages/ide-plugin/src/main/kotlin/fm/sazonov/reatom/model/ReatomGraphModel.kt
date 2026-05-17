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

package fm.sazonov.reatom.model

/** A node summary — what Code Lens and the gutter icon tooltip show. */
data class ReatomNodeSummary(
    val node: ReatomGraphNode,
    val readers: Int,
    val writers: Int,
)

/**
 * Pure operations on the graph model: computing summaries and selecting nodes
 * by file. With no platform dependencies — and tested from here.
 */
object ReatomGraphModel {

    /** The nodes declared in the given file (by absolute path). */
    fun nodesInFile(graph: ReatomGraph, filePath: String): List<ReatomGraphNode> =
        graph.nodes.filter { it.file == filePath }

    /** The usages of a unit — the edges leading into it (readers and writers). */
    fun usagesOf(graph: ReatomGraph, nodeId: String): List<ReatomGraphEdge> =
        graph.edges.filter { it.to == nodeId }

    /** A summary for each node: the count of readers and writers from the graph edges. */
    fun summarize(graph: ReatomGraph): Map<String, ReatomNodeSummary> {
        val readers = HashMap<String, Int>()
        val writers = HashMap<String, Int>()
        for (edge in graph.edges) {
            when (edge.kind) {
                "read" -> readers.merge(edge.to, 1, Int::plus)
                "write" -> writers.merge(edge.to, 1, Int::plus)
            }
        }
        return graph.nodes.associate { node ->
            node.id to ReatomNodeSummary(
                node = node,
                readers = readers[node.id] ?: 0,
                writers = writers[node.id] ?: 0,
            )
        }
    }

    /** The Code Lens text: `atom · ↑4 · ↓2 · ⤴withCache`. */
    fun lensText(summary: ReatomNodeSummary): String {
        val parts = mutableListOf(
            summary.node.kind,
            "↑${summary.readers}",
            "↓${summary.writers}",
        )
        if (summary.node.extensions.isNotEmpty()) {
            parts += "⤴" + summary.node.extensions.joinToString(", ")
        }
        return parts.joinToString(" · ")
    }
}
