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
    fun extractBundleWritesTheResourceToTheTarget() {
        val target = File(temp.newFolder("out"), "analyzer.cjs")
        val result = ReatomAnalyzerLocator.extractBundle(TEST_BUNDLE_RESOURCE, target)

        assertEquals(target, result)
        assertTrue(target.isFile)
        val expected = javaClass.classLoader
            .getResourceAsStream(TEST_BUNDLE_RESOURCE)!!
            .use { it.readBytes() }
        assertArrayEquals(expected, target.readBytes())
    }

    @Test
    fun extractBundleReusesAnExistingTarget() {
        val target = File(temp.newFolder("out"), "analyzer.cjs")
        target.writeText("already extracted")

        val result = ReatomAnalyzerLocator.extractBundle(TEST_BUNDLE_RESOURCE, target)

        assertEquals(target, result)
        // An existing non-empty file is reused, not overwritten.
        assertEquals("already extracted", target.readText())
    }

    @Test
    fun extractBundleReturnsNullForAMissingResource() {
        val target = File(temp.newFolder("out"), "analyzer.cjs")
        assertNull(ReatomAnalyzerLocator.extractBundle("analyzer/no-such-bundle.cjs", target))
        assertFalse(target.exists())
    }

    // --- findUpwards ----------------------------------------------------------

    @Test
    fun findUpwardsLocatesAFileInTheStartDirectory() {
        val dir = temp.newFolder("project")
        val tsconfig = File(dir, "tsconfig.json").apply { writeText("{}") }
        assertEquals(tsconfig, ReatomAnalyzerLocator.findUpwards(dir, "tsconfig.json"))
    }

    @Test
    fun findUpwardsLocatesAFileInAParentDirectory() {
        val root = temp.newFolder("project")
        val tsconfig = File(root, "tsconfig.json").apply { writeText("{}") }
        val nested = File(root, "src/feature").apply { check(mkdirs()) }
        assertEquals(tsconfig, ReatomAnalyzerLocator.findUpwards(nested, "tsconfig.json"))
    }

    @Test
    fun findUpwardsReturnsNullWhenTheFileIsAbsent() {
        val dir = temp.newFolder("project")
        assertNull(ReatomAnalyzerLocator.findUpwards(dir, "tsconfig.json"))
    }

    private companion object {
        const val TEST_BUNDLE_RESOURCE = "reatom-analyzer-test-bundle.cjs"
    }
}
