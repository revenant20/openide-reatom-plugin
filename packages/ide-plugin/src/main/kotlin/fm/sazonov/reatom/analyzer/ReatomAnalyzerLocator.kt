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

package fm.sazonov.reatom.analyzer

import com.google.gson.JsonParser
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.annotations.VisibleForTesting
import java.io.File

/** Resolved paths needed to run the reactive graph analyzer. */
data class AnalyzerLocations(
    val node: File,
    val analyzerCli: File,
    val tsconfig: File,
)

/**
 * Decides whether to run the analyzer and, if so, with what and against what.
 *
 * It first checks that the project **actually uses Reatom** (depends on
 * `@reatom/core`). Then it extracts the **self-contained analyzer bundle** that
 * the IDE plugin carries inside itself (our code plus TypeScript within a single
 * `.cjs`) and looks for `node` and `tsconfig.json`. The consumer does NOT need
 * the `@openide/reatom-ts-plugin` npm package — the IDE plugin is self-contained.
 *
 * The lookup is best-effort: if something is missing, the analyzer simply does
 * not run.
 */
object ReatomAnalyzerLocator {

    private const val REATOM_PACKAGE = "@reatom/core"

    private const val PLUGIN_ID = "fm.sazonov.reatom"

    /** Path of the analyzer bundle inside the plugin jar (see build.gradle.kts). */
    private const val BUNDLE_RESOURCE = "analyzer/reatom-analyzer.cjs"

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
        val analyzer = bundledAnalyzer() ?: return null
        val tsconfig = findUpwards(base, "tsconfig.json") ?: return null
        return AnalyzerLocations(node, analyzer, tsconfig)
    }

    /**
     * Whether the project uses Reatom: `@reatom/core` is declared in the
     * dependencies of some `package.json` up the tree, or installed in
     * `node_modules` (accounts for dependency hoisting in monorepos).
     */
    @VisibleForTesting
    internal fun usesReatom(start: File): Boolean {
        var directory: File? = start
        while (directory != null) {
            val packageJson = File(directory, "package.json")
            if (packageJson.isFile && declaresReatom(packageJson)) return true
            if (File(directory, "node_modules/$REATOM_PACKAGE").isDirectory) return true
            directory = directory.parentFile
        }
        return false
    }

    /** Whether `@reatom/core` is declared in the dependency sections of `package.json`. */
    private fun declaresReatom(packageJson: File): Boolean =
        try {
            val root = JsonParser.parseString(packageJson.readText()).asJsonObject
            DEPENDENCY_SECTIONS.any { section ->
                root.getAsJsonObject(section)?.has(REATOM_PACKAGE) == true
            }
        } catch (e: Exception) {
            thisLogger().debug("Reatom: ignoring an unreadable package.json: $packageJson", e)
            false
        }

    /**
     * Extracts the analyzer bundle from the plugin resources into the IDE
     * system directory (once per plugin version) and returns its path.
     */
    private fun bundledAnalyzer(): File? {
        val version = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "dev"
        val target = File(PathManager.getSystemPath(), "reatom-analyzer/analyzer-$version.cjs")
        return extractBundle(BUNDLE_RESOURCE, target)
    }

    /**
     * Extracts the classpath resource [resourceName] into [target] via an
     * atomic temp-file rename. Idempotent: an existing non-empty [target] is
     * reused as is. Returns [target], or null if the resource is absent or
     * extraction fails.
     */
    @VisibleForTesting
    internal fun extractBundle(resourceName: String, target: File): File? {
        if (target.isFile && target.length() > 0L) return target
        return try {
            val resource = ReatomAnalyzerLocator::class.java.classLoader
                .getResourceAsStream(resourceName)
                ?: run {
                    thisLogger().warn("Reatom: analyzer bundle not found in the plugin")
                    return null
                }
            val directory = target.parentFile
            if (!directory.isDirectory && !directory.mkdirs()) {
                thisLogger().warn("Reatom: cannot create the analyzer cache directory $directory")
                return null
            }
            val tmp = File.createTempFile("analyzer-", ".cjs", directory)
            resource.use { input -> tmp.outputStream().use(input::copyTo) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                if (!tmp.delete()) tmp.deleteOnExit()
            }
            target
        } catch (e: Exception) {
            thisLogger().warn("Reatom: failed to extract the analyzer bundle", e)
            null
        }
    }

    private fun findNode(): File? {
        PathEnvironmentVariableUtil.findInPath("node")?.let { return it }
        return NODE_FALLBACKS.map(::File).firstOrNull { it.canExecute() }
    }

    /** Looks for `relative` in `start` and up the directory tree. */
    @VisibleForTesting
    internal fun findUpwards(start: File, relative: String): File? {
        var directory: File? = start
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return null
    }
}
