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
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.security.MessageDigest

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
 * `.cjs`) and looks for `node` and a `tsconfig.json` / `jsconfig.json`. The
 * consumer does NOT need the `@openide/reatom-ts-plugin` npm package — the IDE
 * plugin is self-contained.
 *
 * The lookup is best-effort: if something is missing, the analyzer simply does
 * not run.
 */
object ReatomAnalyzerLocator {

    private const val REATOM_PACKAGE = "@reatom/core"

    /** Path of the analyzer bundle inside the plugin jar (see build.gradle.kts). */
    private const val BUNDLE_RESOURCE = "analyzer/reatom-analyzer.cjs"

    /** Bytes of the SHA-256 digest kept for the cache file name (16 hex chars). */
    private const val HASH_PREFIX_BYTES = 8

    /** Mask reading a [Byte] as an unsigned 0–255 value. */
    private const val BYTE_MASK = 0xff

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

    /** Project-config file names tried, in priority order, at each directory level. */
    private val CONFIG_FILE_NAMES = listOf("tsconfig.json", "jsconfig.json")

    fun locate(project: Project): AnalyzerLocations? {
        val base = project.guessProjectDir()?.let { File(it.path) } ?: return null
        if (!usesReatom(base)) return null
        val node = findNode() ?: return null
        val analyzer = bundledAnalyzer() ?: return null
        val tsconfig = findProjectConfig(base) ?: return null
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
     * Extracts the self-contained analyzer bundle the IDE plugin carries inside
     * itself into the IDE system directory and returns its path. The cache file
     * is named by a content hash, so a new plugin build with a changed analyzer
     * is picked up automatically — even when the plugin version is unchanged.
     */
    private fun bundledAnalyzer(): File? {
        val bundle = ReatomAnalyzerLocator::class.java.classLoader
            .getResourceAsStream(BUNDLE_RESOURCE)
            ?.use { it.readBytes() }
            ?: run {
                thisLogger().warn("Reatom: analyzer bundle not found in the plugin")
                return null
            }
        return extractBundle(bundle, File(PathManager.getSystemPath(), "reatom-analyzer"))
    }

    /**
     * Writes [bundle] into [directory] under a content-hashed file name and
     * returns it: a changed bundle yields a new path, so a stale cache is never
     * reused. A file already extracted from the same bytes is reused as is, and
     * superseded bundles (old version-named or stale-hash files) are removed.
     * Returns null if extraction fails.
     */
    @VisibleForTesting
    internal fun extractBundle(bundle: ByteArray, directory: File): File? {
        val target = File(directory, "analyzer-${contentHash(bundle)}.cjs")
        val upToDate = target.isFile && target.length() == bundle.size.toLong()
        if (!upToDate && !writeBundle(bundle, target)) return null
        removeSupersededBundles(directory, keep = target)
        return target
    }

    /** Writes [bundle] to [target] via an atomic temp-file rename. */
    private fun writeBundle(bundle: ByteArray, target: File): Boolean {
        val directory = target.parentFile
        if (!directory.isDirectory && !directory.mkdirs()) {
            thisLogger().warn("Reatom: cannot create the analyzer cache directory $directory")
            return false
        }
        return try {
            val tmp = File.createTempFile(".analyzer-", ".cjs.tmp", directory)
            tmp.writeBytes(bundle)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                if (!tmp.delete()) tmp.deleteOnExit()
            }
            true
        } catch (e: Exception) {
            thisLogger().warn("Reatom: failed to extract the analyzer bundle", e)
            false
        }
    }

    /** Deletes previously extracted analyzer bundles other than [keep]. */
    private fun removeSupersededBundles(directory: File, keep: File) {
        directory.listFiles { file ->
            file.isFile &&
                file.name.startsWith("analyzer-") &&
                file.name.endsWith(".cjs") &&
                file != keep
        }?.forEach { if (!it.delete()) it.deleteOnExit() }
    }

    /** Short, stable content hash for naming the analyzer cache file. */
    private fun contentHash(bundle: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bundle)
            .take(HASH_PREFIX_BYTES)
            .joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    private fun findNode(): File? {
        PathEnvironmentVariableUtil.findInPath("node")?.let { return it }
        return NODE_FALLBACKS.map(::File).firstOrNull { it.canExecute() }
    }

    /**
     * Looks for a TypeScript/JavaScript project config in [start] and up the
     * directory tree. At each level it prefers `tsconfig.json`, then
     * `jsconfig.json`, then — as a fallback for solution-style layouts that lack
     * a root `tsconfig.json` — the alphabetically first `tsconfig*.json`. The
     * closest config wins: a child's `jsconfig.json` beats a parent's
     * `tsconfig.json`.
     */
    @VisibleForTesting
    internal fun findProjectConfig(start: File): File? {
        var directory: File? = start
        while (directory != null) {
            for (name in CONFIG_FILE_NAMES) {
                val candidate = File(directory, name)
                if (candidate.isFile) return candidate
            }
            directory.listFiles { file ->
                file.isFile &&
                    file.name.startsWith("tsconfig") &&
                    file.name.endsWith(".json")
            }?.minByOrNull { it.name }?.let { return it }
            directory = directory.parentFile
        }
        return null
    }
}
