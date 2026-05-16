package ru.openide.reatom.analyzer

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
 * Ищет, чем и что запускать: исполняемый `node`, CLI анализатора
 * (`@openide/reatom-ts-plugin`) и `tsconfig.json` проекта. Поиск best-effort:
 * если чего-то нет — анализатор просто не запускается.
 */
object ReatomAnalyzerLocator {

    private const val CLI_RELATIVE =
        "node_modules/@openide/reatom-ts-plugin/dist/analyzer/cli.js"

    private val NODE_FALLBACKS = listOf(
        "/opt/homebrew/bin/node",
        "/usr/local/bin/node",
        "/usr/bin/node",
    )

    fun locate(project: Project): AnalyzerLocations? {
        val base = project.guessProjectDir()?.let { File(it.path) } ?: return null
        val node = findNode() ?: return null
        val cli = findUpwards(base, CLI_RELATIVE) ?: return null
        val tsconfig = findUpwards(base, "tsconfig.json") ?: return null
        return AnalyzerLocations(node, cli, tsconfig)
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
