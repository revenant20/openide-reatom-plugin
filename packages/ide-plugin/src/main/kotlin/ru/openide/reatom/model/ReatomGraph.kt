package ru.openide.reatom.model

/**
 * Модель реактивного графа — контракт с анализатором `@openide/reatom-ts-plugin`
 * (`src/analyzer/graph.ts`). Поля и значения по умолчанию подобраны под Gson:
 * десериализация JSON, который печатает `node cli.js`.
 */

/** Диапазон в файле в offset'ах. */
data class GraphRange(
    val start: Int = 0,
    val end: Int = 0,
)

/** Узел графа — объявление Reatom-юнита. */
data class ReatomGraphNode(
    val id: String = "",
    /** `atom` | `computed` | `action` | `effect`. */
    val kind: String = "",
    val name: String = "",
    /** Абсолютный путь файла объявления. */
    val file: String = "",
    val range: GraphRange = GraphRange(),
    val extensions: List<String> = emptyList(),
)

/** Ребро графа — одно использование юнита. */
data class ReatomGraphEdge(
    /** id используемого юнита. */
    val to: String = "",
    /** id объемлющего юнита, если использование внутри него. */
    val from: String? = null,
    /** `read` | `write` | `extend`. */
    val kind: String = "",
    val file: String = "",
    val range: GraphRange = GraphRange(),
)

/** Граф целиком — выход анализатора. */
data class ReatomGraph(
    val schemaVersion: Int = 0,
    val nodes: List<ReatomGraphNode> = emptyList(),
    val edges: List<ReatomGraphEdge> = emptyList(),
)
