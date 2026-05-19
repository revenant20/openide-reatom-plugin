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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [ReatomAnalyzerLocator]: the "project uses Reatom" gate and
 * the extraction of the self-contained analyzer bundle.
 */
class ReatomAnalyzerLocatorTest {

    @JvmField
    @Rule
    val temp = TemporaryFolder()

    private fun packageJson(dir: File, body: String): File =
        File(dir, "package.json").apply { writeText(body) }

    // --- usesReatom -----------------------------------------------------------

    @Test
    fun usesReatomDetectsARuntimeDependency() {
        val dir = temp.newFolder("project")
        packageJson(dir, """{ "dependencies": { "@reatom/core": "^1001.0.0" } }""")
        assertTrue(ReatomAnalyzerLocator.usesReatom(dir))
    }

    @Test
    fun usesReatomDetectsADevDependency() {
        val dir = temp.newFolder("project")
        packageJson(dir, """{ "devDependencies": { "@reatom/core": "^1001.0.0" } }""")
        assertTrue(ReatomAnalyzerLocator.usesReatom(dir))
    }

    @Test
    fun usesReatomDetectsAPeerDependency() {
        val dir = temp.newFolder("project")
        packageJson(dir, """{ "peerDependencies": { "@reatom/core": "*" } }""")
        assertTrue(ReatomAnalyzerLocator.usesReatom(dir))
    }

    @Test
    fun usesReatomIsFalseWithoutTheDependency() {
        val dir = temp.newFolder("project")
        packageJson(dir, """{ "dependencies": { "react": "^19.0.0" } }""")
        assertFalse(ReatomAnalyzerLocator.usesReatom(dir))
    }

    @Test
    fun usesReatomIsFalseWithoutAPackageJson() {
        assertFalse(ReatomAnalyzerLocator.usesReatom(temp.newFolder("empty")))
    }

    @Test
    fun usesReatomWalksUpToAParentPackageJson() {
        val root = temp.newFolder("monorepo")
        packageJson(root, """{ "dependencies": { "@reatom/core": "^1001.0.0" } }""")
        val nested = File(root, "packages/feature/src").apply { check(mkdirs()) }
        assertTrue(ReatomAnalyzerLocator.usesReatom(nested))
    }

    @Test
    fun usesReatomDetectsAHoistedNodeModules() {
        val dir = temp.newFolder("project")
        packageJson(dir, """{ "dependencies": { "some-lib": "^1.0.0" } }""")
        check(File(dir, "node_modules/@reatom/core").mkdirs())
        assertTrue(ReatomAnalyzerLocator.usesReatom(dir))
    }

    @Test
    fun usesReatomIgnoresAMalformedPackageJson() {
        val dir = temp.newFolder("project")
        packageJson(dir, "{ this is not json")
        assertFalse(ReatomAnalyzerLocator.usesReatom(dir))
    }

    // --- extractBundle --------------------------------------------------------

    @Test
    fun extractBundleWritesAContentHashedFile() {
        val dir = temp.newFolder("cache")
        val bundle = "analyzer-bundle-bytes".toByteArray()

        val result = ReatomAnalyzerLocator.extractBundle(bundle, dir)!!

        assertEquals(dir, result.parentFile)
        assertTrue(result.name.matches(Regex("analyzer-[0-9a-f]+\\.cjs")))
        assertArrayEquals(bundle, result.readBytes())
    }

    @Test
    fun extractBundleReusesAnAlreadyExtractedFile() {
        val dir = temp.newFolder("cache")
        val bundle = "twelve-bytes".toByteArray()
        val target = ReatomAnalyzerLocator.extractBundle(bundle, dir)!!
        // Same length, different content: a reuse keeps it, a rewrite replaces it.
        target.writeBytes("XXXXXXXXXXXX".toByteArray())

        val again = ReatomAnalyzerLocator.extractBundle(bundle, dir)!!

        assertEquals(target, again)
        assertEquals("XXXXXXXXXXXX", again.readText())
    }

    @Test
    fun extractBundleNamesDifferentContentDifferently() {
        val dir = temp.newFolder("cache")
        val a = ReatomAnalyzerLocator.extractBundle("bundle-a".toByteArray(), dir)!!
        val b = ReatomAnalyzerLocator.extractBundle("bundle-b".toByteArray(), dir)!!
        assertNotEquals(a.name, b.name)
    }

    @Test
    fun extractBundleRemovesSupersededBundles() {
        val dir = temp.newFolder("cache")
        // A stale cache file from the earlier version-named scheme.
        val stale = File(dir, "analyzer-0.0.1.cjs").apply { writeText("stale") }

        val current = ReatomAnalyzerLocator.extractBundle("fresh".toByteArray(), dir)!!

        assertTrue(current.isFile)
        assertFalse(stale.exists())
    }

    // --- findProjectConfig ----------------------------------------------------

    @Test
    fun findProjectConfigLocatesTsconfigInTheStartDirectory() {
        val dir = temp.newFolder("project")
        val tsconfig = File(dir, "tsconfig.json").apply { writeText("{}") }
        assertEquals(tsconfig, ReatomAnalyzerLocator.findProjectConfig(dir))
    }

    @Test
    fun findProjectConfigLocatesTsconfigInAParentDirectory() {
        val root = temp.newFolder("project")
        val tsconfig = File(root, "tsconfig.json").apply { writeText("{}") }
        val nested = File(root, "src/feature").apply { check(mkdirs()) }
        assertEquals(tsconfig, ReatomAnalyzerLocator.findProjectConfig(nested))
    }

    @Test
    fun findProjectConfigReturnsNullWhenNoConfigExists() {
        val dir = temp.newFolder("project")
        assertNull(ReatomAnalyzerLocator.findProjectConfig(dir))
    }

    @Test
    fun findProjectConfigFallsBackToJsconfig() {
        val dir = temp.newFolder("project")
        val jsconfig = File(dir, "jsconfig.json").apply { writeText("{}") }
        assertEquals(jsconfig, ReatomAnalyzerLocator.findProjectConfig(dir))
    }

    @Test
    fun findProjectConfigPrefersTsconfigOverJsconfig() {
        val dir = temp.newFolder("project")
        val tsconfig = File(dir, "tsconfig.json").apply { writeText("{}") }
        File(dir, "jsconfig.json").writeText("{}")
        assertEquals(tsconfig, ReatomAnalyzerLocator.findProjectConfig(dir))
    }

    @Test
    fun findProjectConfigFallsBackToANamedTsconfigVariant() {
        val dir = temp.newFolder("project")
        File(dir, "tsconfig.base.json").writeText("{}")
        val appConfig = File(dir, "tsconfig.app.json").apply { writeText("{}") }
        // No tsconfig.json / jsconfig.json — the alphabetically first variant wins.
        assertEquals(appConfig, ReatomAnalyzerLocator.findProjectConfig(dir))
    }

    @Test
    fun findProjectConfigPrefersTheClosestConfig() {
        val root = temp.newFolder("project")
        File(root, "tsconfig.json").writeText("{}")
        val nested = File(root, "packages/app").apply { check(mkdirs()) }
        val nestedConfig = File(nested, "jsconfig.json").apply { writeText("{}") }
        // The nested jsconfig.json beats the root tsconfig.json.
        assertEquals(nestedConfig, ReatomAnalyzerLocator.findProjectConfig(nested))
    }
}
