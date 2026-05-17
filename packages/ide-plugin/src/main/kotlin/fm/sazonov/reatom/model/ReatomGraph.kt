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

/**
 * The reactive graph model — the contract with the `@openide/reatom-ts-plugin`
 * analyzer (`src/analyzer/graph.ts`). The fields and default values are chosen
 * for Gson: deserialization of the JSON that `node cli.js` prints.
 */

/** A range in a file, in offsets. */
data class GraphRange(
    val start: Int = 0,
    val end: Int = 0,
)

/** A graph node — a Reatom unit declaration. */
data class ReatomGraphNode(
    val id: String = "",
    /** `atom` | `computed` | `action` | `effect`. */
    val kind: String = "",
    val name: String = "",
    /** The absolute path of the declaration file. */
    val file: String = "",
    val range: GraphRange = GraphRange(),
    val extensions: List<String> = emptyList(),
)

/** A graph edge — a single usage of a unit. */
data class ReatomGraphEdge(
    /** The id of the used unit. */
    val to: String = "",
    /** The id of the enclosing unit, if the usage is inside it. */
    val from: String? = null,
    /** `read` | `write` | `extend`. */
    val kind: String = "",
    val file: String = "",
    val range: GraphRange = GraphRange(),
)

/** The graph as a whole — the analyzer output. */
data class ReatomGraph(
    val schemaVersion: Int = 0,
    val nodes: List<ReatomGraphNode> = emptyList(),
    val edges: List<ReatomGraphEdge> = emptyList(),
)
