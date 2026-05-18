import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.github.spotbugs") version "6.5.4"
}

group = "fm.sazonov.reatom"
version = "0.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IntelliJ IDEA 2025.3 platform (build 253) — the OpenIDE/IDEA target.
        // The idea:idea artifact is the same one used by the neighboring php-plugin.
        intellijIdea("2025.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // MCP Steroid — optional AI-driven IDE control in the sandbox, used by
        // the maintainer for testing. Opt-in: set the `mcpSteroidDir` Gradle
        // property (e.g. in ~/.gradle/gradle.properties) to the directory that
        // holds the mcp-steroid-*.zip distribution. Without the property the
        // sandbox runs plain — regular builds and tests are unaffected.
        val mcpSteroidZip = providers.gradleProperty("mcpSteroidDir").orNull
            ?.let { dir -> fileTree(dir) { include("mcp-steroid-*.zip") }.files.firstOrNull() }
        if (mcpSteroidZip?.isFile == true) {
            bundledPlugin("com.intellij.mcpServer") // MCP Steroid depends on it
            localPlugin(mcpSteroidZip.absolutePath)
            logger.lifecycle("[reatom-ide-plugin] MCP Steroid enabled for the sandbox.")
        }
    }
    testImplementation("junit:junit:4.13.2")

    // detekt-formatting — the ktlint-backed `formatting` rule set. It honours
    // the repository .editorconfig and most of its findings are auto-correctable.
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            // IntelliJ IDEA 2025.3 through 2026.3.
            untilBuild = "263.*"
        }
    }

    // `verifyPlugin` runs the JetBrains Plugin Verifier — it checks the built
    // plugin for API/binary compatibility against real IDE builds, the same
    // check the Marketplace performs. Verified at both ends of the supported
    // range. Since 2025.3 IntelliJ IDEA ships as a single unified distribution
    // (`IntellijIdea`) — Community/Ultimate are no longer published separately.
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, "2025.3")
            create(IntelliJPlatformType.IntellijIdea, "2026.1")
        }
    }
}

tasks {
    // Indexing searchable options spins up a headless IDE and is not needed by the plugin.
    buildSearchableOptions {
        enabled = false
    }

    // The self-contained analyzer bundle (our code + TypeScript inside) ships
    // as a resource inside the plugin — the consumer does not need the
    // @openide/reatom-ts-plugin npm package. The bundle is built by the
    // :ts-plugin subproject.
    processResources {
        dependsOn(":ts-plugin:buildAnalyzer")
        from(rootProject.file("packages/ts-plugin/dist/analyzer/reatom-analyzer.cjs")) {
            into("analyzer")
        }
    }
}

// The sandbox auto-opens the passed project — trust it without a dialog.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Didea.trust.all.projects=true")
    })
}

kotlin {
    // JDK 21 toolchain — Gradle locates the JDK itself (or downloads it via the
    // foojay resolver in settings.gradle.kts), so the build needs no JAVA_HOME
    // setup. This also pins the Kotlin/Java bytecode target to 21.
    jvmToolchain(21)
}

// Static analysis of the Kotlin sources. detekt runs as part of `check` (and
// therefore `build`); it builds on detekt's bundled default rule set, with
// project-specific tweaks kept in config/detekt/detekt.yml.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    basePath = rootProject.projectDir.absolutePath
}

// Run detekt with type resolution. The `detektMain` / `detektTest` tasks see
// the full compile classpath, which activates the rules that need type
// information (deprecation, nullable-receiver calls, unreachable catch blocks,
// coroutine checks) — those are silently skipped by the plain `detekt` task.
// `detekt` is disabled so the analysis does not also run untyped.
tasks.named("detekt") { enabled = false }
tasks.named("check") { dependsOn("detektMain", "detektTest") }

// `./gradlew detektMain detektTest -PdetektAutoCorrect` rewrites sources in
// place to fix the auto-correctable findings (most detekt-formatting rules).
// Without the property — including every `check` run — detekt only reports.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    autoCorrect = providers.gradleProperty("detektAutoCorrect").isPresent
}

// Bytecode-level static analysis (the FindBugs successor). `spotbugsMain` /
// `spotbugsTest` run as part of `check`. It complements detekt rather than
// duplicating it: detekt reads the Kotlin AST, whereas SpotBugs inspects
// compiled `.class` files and so catches a different class of defects (e.g.
// ignored JDK return values, data-flow bugs). Because it sees Kotlin only
// through generated bytecode, config/spotbugs/exclude.xml filters the patterns
// that are noise on a Kotlin codebase — see that file for the per-pattern
// rationale.
spotbugs {
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = rootProject.file("config/spotbugs/exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}
