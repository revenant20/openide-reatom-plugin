import com.github.gradle.node.npm.task.NpmTask

plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

/**
 * `ts-plugin` — a reactive graph analyzer written in TypeScript. Gradle here
 * is a thin wrapper over npm: node-gradle downloads the required Node version
 * itself, so the build is reproducible and independent of the system Node.
 *
 * The npm workspace is declared at the repository root — that is where
 * node-gradle looks.
 */
node {
    version.set("22.11.0")
    download.set(true)
    nodeProjectDir.set(rootProject.projectDir)
}

// Builds a self-contained analyzer bundle (tsc + esbuild) →
// dist/analyzer/reatom-analyzer.cjs. The :ide-plugin subproject ships it as a resource.
val buildAnalyzer = tasks.register<NpmTask>("buildAnalyzer") {
    dependsOn("npmInstall")
    args.set(listOf("run", "build", "--workspace", "@openide/reatom-ts-plugin"))
    inputs.dir("src")
    inputs.files("package.json", "tsconfig.json")
    outputs.dir("dist")
}

// Analyzer tests (vitest).
val npmTest = tasks.register<NpmTask>("test") {
    dependsOn("npmInstall")
    args.set(listOf("run", "test", "--workspace", "@openide/reatom-ts-plugin"))
}

// Static analysis of the TypeScript sources (ESLint).
// Configured in packages/ts-plugin/eslint.config.mjs.
val npmLint = tasks.register<NpmTask>("lint") {
    dependsOn("npmInstall")
    args.set(listOf("run", "lint", "--workspace", "@openide/reatom-ts-plugin"))
}

tasks.register("build") { dependsOn(buildAnalyzer) }
tasks.register("check") { dependsOn(npmTest, npmLint) }
