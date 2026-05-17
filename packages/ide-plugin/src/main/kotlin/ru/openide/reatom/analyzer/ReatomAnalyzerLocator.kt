package ru.openide.reatom.analyzer

import com.google.gson.JsonParser
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.io.File

/** Найденные пути для запуска анализатора реактивного графа. */
data class AnalyzerLocations(
    val node: File,
    val analyzerCli: File,
    val tsconfig: File,
)

/**
 * Решает, запускать ли анализатор, и если да — чем и что.
 *
 * Сначала проверяет, что проект **реально использует Reatom** (зависит от
 * `@reatom/core`). Сам по себе установленный `@openide/reatom-ts-plugin`
 * сигналом не считается: IDE-плагин может ставить его сам, поэтому его наличие
 * не означает, что перед нами Reatom-проект.
 *
 * Затем ищет, чем и что запускать: исполняемый `node`, CLI анализатора
 * (`@openide/reatom-ts-plugin`) и `tsconfig.json`. Поиск best-effort: если
 * чего-то нет — анализатор просто не запускается.
 */
object ReatomAnalyzerLocator {

    private const val CLI_RELATIVE =
        "node_modules/@openide/reatom-ts-plugin/dist/analyzer/cli.js"

    private const val REATOM_PACKAGE = "@reatom/core"

    private val DEPENDENCY_SECTIONS = listOf(
        "dependencies",
        "devDependencies",
        "peerDependencies",
    )

    private val NODE_FALLBACKS = listOf(
        "/opt/homebrew/bin/node",
        "/usr/local/bin/node",
        "/usr/bin/node",
    )

    fun locate(project: Project): AnalyzerLocations? {
        val base = project.guessProjectDir()?.let { File(it.path) } ?: return null
        if (!usesReatom(base)) return null
        val node = findNode() ?: return null
        val cli = findUpwards(base, CLI_RELATIVE) ?: return null
        val tsconfig = findUpwards(base, "tsconfig.json") ?: return null
        return AnalyzerLocations(node, cli, tsconfig)
    }

    /**
     * Использует ли проект Reatom: `@reatom/core` объявлен в зависимостях
     * какого-либо `package.json` вверх по дереву либо установлен в
     * `node_modules` (учитывает hoisting зависимостей в монорепозиториях).
     */
    private fun usesReatom(start: File): Boolean {
        var directory: File? = start
        while (directory != null) {
            val packageJson = File(directory, "package.json")
            if (packageJson.isFile && declaresReatom(packageJson)) return true
            if (File(directory, "node_modules/$REATOM_PACKAGE").isDirectory) return true
            directory = directory.parentFile
        }
        return false
    }

    /** Объявлен ли `@reatom/core` в секциях зависимостей `package.json`. */
    private fun declaresReatom(packageJson: File): Boolean =
        try {
            val root = JsonParser.parseString(packageJson.readText()).asJsonObject
            DEPENDENCY_SECTIONS.any { section ->
                root.getAsJsonObject(section)?.has(REATOM_PACKAGE) == true
            }
        } catch (e: Exception) {
            false
        }

    private fun findNode(): File? {
        PathEnvironmentVariableUtil.findInPath("node")?.let { return it }
        return NODE_FALLBACKS.map(::File).firstOrNull { it.canExecute() }
    }

    /** Ищет `relative` в `start` и вверх по дереву каталогов. */
    private fun findUpwards(start: File, relative: String): File? {
        var directory: File? = start
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return null
    }
}
