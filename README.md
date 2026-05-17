# OpenIDE Reatom Plugin

[Русский](README.ru.md)

Support for the [Reatom v1001](https://v1001.reatom.dev) reactive state manager
in OpenIDE and IntelliJ IDEA — navigate the reactive graph right in the editor.

An open-source product under the Apache 2.0 license. Only Reatom **v1001** is
supported.

## What it does

For `atom` / `computed` / `action` / `effect` declarations in a project the IDE
plugin provides:

- **Code Lens** — a summary line above a declaration: the unit's role and the
  reactive-link counters (`atom · ↑4 · ↓2`);
- **gutter icons** — on a declaration, separate icons for readers and writers
  (a click opens a popup with the usages); on a usage line, an icon that jumps
  to the declaration;
- **navigation** "usage ↔ declaration", including across files.

The links come from static code analysis — without running the application.

## Repository structure

A monorepo of two packages with a single Gradle build:

- **`packages/ts-plugin`** (`@openide/reatom-ts-plugin`) — the reactive graph
  analyzer built on the TypeScript Compiler API. It parses the project and
  builds a JSON model: `atom` / `computed` / `action` / `effect` nodes and
  `read` / `write` / `extend` edges. esbuild packs it into a self-contained
  bundle.
- **`packages/ide-plugin`** — the IDE plugin on the IntelliJ platform (Kotlin).
  It draws the Code Lens, gutter icons and navigation, and **ships the analyzer
  inside itself** — the consumer needs no npm package, a `@reatom/core`
  dependency in the project is enough.

## Build

The repository is a single Gradle multi-project; build from the root:

```bash
# build and check everything: the analyzer, the IDE plugin, the tests
./gradlew build

# the IDE plugin distribution → packages/ide-plugin/build/distributions/*.zip
./gradlew :ide-plugin:buildPlugin
```

Gradle provisions everything itself — the JDK 21 toolchain, the IntelliJ IDEA
2025.3 platform, and the Node runtime for building the analyzer — so no
`JAVA_HOME` or SDK setup is needed (any JDK is enough to launch Gradle). The
plugin is not yet published to the marketplace — it is installed from the built
distribution.

## Demo

`examples/reatom-demo` is a small `@reatom/core` consumer project — a toy
shopping cart covering `atom` / `computed` / `action` / `effect`, an extension,
and cross-file links. Open it in an IDE with the plugin to see the Code Lens
and gutter icons:

```bash
cd examples/reatom-demo && npm install
```

`packages/ide-plugin/scripts/start-sandbox.sh` brings up an IDE sandbox with
the plugin already installed and opens this demo by default.

## Status

Early development. Implemented:

- the **reactive graph analyzer** (`ts-plugin`);
- **native Code Lens, gutter icons and navigation** (`ide-plugin`).

The full concept and the not-yet-implemented features (diagnostics, quick-fixes,
completion snippets, a graph toolwindow, and more) are described in
[docs/features-reatom-plugin.md](docs/features-reatom-plugin.md).

## Documentation

- [docs/features-reatom-plugin.md](docs/features-reatom-plugin.md) — the
  concept, architecture and feature set;
- [AGENTS.md](AGENTS.md) — project description and contributor working rules
  (the [agents.md](https://agents.md/) format).

## License

[Apache License 2.0](LICENSE). © 2026 Fedor Sazonov.
