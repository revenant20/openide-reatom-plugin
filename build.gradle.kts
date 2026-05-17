// Root aggregator project of the monorepo. Each plugin is built in its own
// subproject:
//   :ide-plugin — IntelliJ plugin in Kotlin (Code Lens, gutter icons, navigation);
//   :ts-plugin  — reactive graph analyzer in TypeScript (npm via node-gradle).
//
// `./gradlew build` builds and verifies the entire repository.
