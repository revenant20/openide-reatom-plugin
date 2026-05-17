import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

/**
 * `ts-plugin` — анализатор реактивного графа на TypeScript. Gradle здесь —
 * тонкая обёртка над npm: node-gradle сам скачивает Node нужной версии, так
 * что сборка воспроизводима и не зависит от системного Node.
 *
 * npm-workspace объявлен в корне репозитория — туда и смотрит node-gradle.
 */
node {
    version.set("22.11.0")
    download.set(true)
    nodeProjectDir.set(rootProject.projectDir)
}

// Собирает самодостаточный бандл анализатора (tsc + esbuild) →
// dist/analyzer/reatom-analyzer.cjs. Его ресурсом возит подпроект :ide-plugin.
val buildAnalyzer = tasks.register<NpmTask>("buildAnalyzer") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build", "--workspace", "@openide/reatom-ts-plugin"))
    inputs.dir("src")
    inputs.files("package.json", "tsconfig.json")
    outputs.dir("dist")
}

// Тесты анализатора (vitest).
val npmTest = tasks.register<NpmTask>("test") {
    dependsOn("npmInstall")
    args.set(listOf("run", "test", "--workspace", "@openide/reatom-ts-plugin"))
}

tasks.register("build") { dependsOn(buildAnalyzer) }
tasks.register("check") { dependsOn(npmTest) }
