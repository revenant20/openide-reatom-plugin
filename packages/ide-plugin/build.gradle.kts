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

        // The platform MCP Server — the MCP infrastructure inside the IDE.
        bundledPlugin("com.intellij.mcpServer")

        // MCP Steroid — installed into the sandbox as a localPlugin from a
        // local mcp-steroid build (the same mechanism as in openide-mcp). It
        // gives the AI agent full control of the IDE. The path is overridden
        // via -PmcpSteroidJar=... It is included only if the ZIP has been
        // built — otherwise buildPlugin/test work without it.
        val mcpSteroidZip = providers.gradleProperty("mcpSteroidJar").orNull?.let(::file)
            ?: fileTree("${rootProject.projectDir}/../mcp-steroid/ij-plugin/build/distributions") {
                include("mcp-steroid-*.zip")
            }.files.firstOrNull()
        if (mcpSteroidZip?.isFile == true) {
            localPlugin(mcpSteroidZip.absolutePath)
        } else {
            logger.lifecycle("[reatom-ide-plugin] MCP Steroid ZIP not found — sandbox without MCP Steroid.")
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
