plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "ru.openide.reatom"
version = "0.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Платформа IntelliJ IDEA 2025.3 (build 253) — таргет OpenIDE/IDEA.
        // Артефакт idea:idea — тот же, что у соседнего php-plugin.
        intellijIdea("2025.3")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Платформенный MCP Server — инфраструктура MCP в IDE.
        bundledPlugin("com.intellij.mcpServer")

        // MCP Steroid — ставится в песочницу как localPlugin из локальной
        // сборки mcp-steroid (тем же механизмом, что в openide-mcp). Даёт
        // AI-агенту полный контроль IDE. Путь переопределяется -PmcpSteroidJar=...
        // Подключается, только если ZIP собран — иначе buildPlugin/test
        // работают без него.
        val mcpSteroidZip = providers.gradleProperty("mcpSteroidJar").orNull?.let(::file)
            ?: fileTree("${rootProject.projectDir}/../mcp-steroid/ij-plugin/build/distributions") {
                include("mcp-steroid-*.zip")
            }.files.firstOrNull()
        if (mcpSteroidZip?.isFile == true) {
            localPlugin(mcpSteroidZip.absolutePath)
        } else {
            logger.lifecycle("[reatom-ide-plugin] MCP Steroid ZIP не найден — песочница без MCP Steroid.")
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
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    // Индексация searchable options поднимает headless-IDE и не нужна плагину.
    buildSearchableOptions {
        enabled = false
    }

    // Самодостаточный бандл анализатора (наш код + TypeScript внутри) едет
    // ресурсом внутри плагина — потребителю не нужен npm-пакет
    // @openide/reatom-ts-plugin. Бандл собирает подпроект :ts-plugin.
    processResources {
        dependsOn(":ts-plugin:buildAnalyzer")
        from(rootProject.file("packages/ts-plugin/dist/analyzer/reatom-analyzer.cjs")) {
            into("analyzer")
        }
    }
}

// Песочница авто-открывает переданный проект — доверяем ему без диалога.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Didea.trust.all.projects=true")
    })
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
