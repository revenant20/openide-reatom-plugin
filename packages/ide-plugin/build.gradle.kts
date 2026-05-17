plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
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
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
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
