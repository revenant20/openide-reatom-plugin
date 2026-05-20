# AGENTS.md

A single file of instructions and context for agents and contributors to the **OpenIDE Reatom Plugin** project. Format — [agents.md](https://agents.md/).

## About the project

Support for the [Reatom v1001](https://v1001.reatom.dev) reactive state manager in OpenIDE and other editors: semantic inspections, reactive graph navigation, snippets, dependency visualization, quick-fixes.

Key decisions:

- We support **only Reatom v1001**. Version v3 is out of scope; the v3 → v1001 migration is a separate class of tasks.
- The product is **free and open** (Free). No licensing, no Pro features.
- The primary target is OpenIDE, but the TS part works in any editor with tsserver.

The feature set and its status — [FEATURES.md](FEATURES.md).

## Repository structure — a monorepo of two plugins

The repository contains **two plugins** that are developed and versioned together:

### 1. TypeScript plugin — `packages/ts-plugin`

The npm package `@openide/reatom-ts-plugin`. **Currently** it is the **reactive graph analyzer** — a reusable core built on the TypeScript Compiler API, run as a plain Node process outside tsserver. Its self-contained bundle ships inside the IDE plugin (feature 9); the same core is meant to back the CLI visualization (feature 7) and the toolwindow (feature 8).

**Planned** — turning it into a plugin for `typescript-language-server` (tsserver): loaded **inside the tsserver process**, with access to the full typed AST and the Type Checker, responsible for all **semantics**:

- diagnostics (inspections) — `.set` inside `computed`, `await` without `wrap`, `subscribe` leaks, `.extend(...)` ordering conflicts, atom naming;
- code actions (quick-fixes / intentions);
- completion, including snippets in snippet format (analogous to live templates);
- hover / quick documentation.

That semantics layer would be delivered over the standard LSP protocol, working **cross-editor**: OpenIDE, VS Code, WebStorm, Neovim, Helix, Zed — anywhere tsserver is available. Inlay hints were tried in it and removed (they duplicated the IDE plugin's Code Lens).

Code Lens via the TS plugin is **not available** (there is no method for it in `ts.LanguageService`), and a categorized Find Usages is not available either (the LSP `references` result is flat).

Stack: TypeScript, TypeScript Compiler API.

### 2. IDE plugin — `packages/ide-plugin`

A plugin on the IntelliJ platform (OpenIDE / IntelliJ IDEA). Its scope is the native IDE capabilities that tsserver and the LSP protocol **cannot** provide:

- native **Code Lens, gutter icons and navigation** on `atom` / `computed` / `action` / `effect` (feature 9) — **implemented**;
- a **toolwindow** with a static reactive graph of atom dependencies (feature 8) — planned;
- bundled **live templates** in the JetBrains format (feature 10) — planned.

In addition, the IDE plugin **ships the graph analyzer inside itself** — a self-contained esbuild bundle (our code plus TypeScript inside a single `.cjs`) — and runs it as a Node process. As a result, the consumer does not need the npm package: a `@reatom/core` dependency in the project is enough. Delivering a future LSP layer into tsserver is an open question.

Stack: Kotlin, Gradle, IntelliJ Platform SDK, Gradle IntelliJ Plugin.

### Separation of responsibilities

| Layer | TS plugin | IDE plugin |
|---|---|---|
| Semantics (inspections, quick-fix, completion, hover) | ✅ | — |
| Cross-editor support | ✅ (any tsserver) | ❌ (IntelliJ platform only) |
| Toolwindow with the graph, native gutter icons | ❌ | ✅ |
| Delivering the analyzer without an npm package on the consumer side | — | ✅ |

Rule: anything that can be done in the TS plugin is done in the TS plugin (for the sake of cross-editor support). The IDE plugin is only for native IDE features that are fundamentally impossible via LSP.

## Directory layout

```
openide-reatom-plugin/
├── AGENTS.md                 # this file
├── CLAUDE.md                 # entry point, points to AGENTS.md
├── FEATURES.md               # the feature set and its status
├── README.md
├── config/                   # static-analysis configs (detekt, SpotBugs)
└── packages/
    ├── ts-plugin/            # @openide/reatom-ts-plugin (TypeScript)
    └── ide-plugin/           # IDE plugin (Kotlin / Gradle / IntelliJ SDK)
```

> Status: the graph analyzer (feature 6) is implemented in `packages/ts-plugin`, and native Code Lens / gutter icons / navigation (feature 9) in `packages/ide-plugin`. Inlay hints (feature 2) were tried via the TS LS plugin but removed — they duplicated Code Lens. The `templates/` directory will appear later.

## Static analysis

Both plugins are checked by static analysers, all wired into `./gradlew check`.
Run it before committing — `check` must stay green. (`./gradlew build` also runs
detekt and SpotBugs, via the IDE plugin's `check`, but not the analyzer's ESLint.)

| Package | Tool | Scope | Config |
|---|---|---|---|
| `ide-plugin` | [detekt](https://detekt.dev) 1.23.8 | Kotlin sources | `config/detekt/detekt.yml` |
| `ide-plugin` | [SpotBugs](https://spotbugs.github.io) (FindBugs successor) | JVM bytecode | `config/spotbugs/exclude.xml` |
| `ts-plugin` | [ESLint](https://eslint.org) + typescript-eslint | TypeScript sources | `packages/ts-plugin/eslint.config.mjs` |

The config files carry on top of each tool's recommended rule set; they only
record the deviations, and every deviation is commented with its rationale.

detekt runs with type resolution (the `detektMain` / `detektTest` tasks) and
includes the ktlint-based `formatting` rule set. Most formatting findings are
auto-correctable — `./gradlew detektMain detektTest -PdetektAutoCorrect`
rewrites the sources in place; a plain `check` run never modifies files. Every
`.kt` file must start with the Apache-2.0 header in
`config/detekt/license.template`.

detekt and SpotBugs are complementary, not redundant: detekt reads the Kotlin
AST (complexity, naming, coroutine and idiom smells), while SpotBugs analyses
compiled bytecode and catches a different class of defects (ignored JDK return
values, data-flow bugs). SpotBugs sees Kotlin only through generated bytecode,
so `config/spotbugs/exclude.xml` filters the patterns that are pure compiler
noise on a Kotlin codebase — genuine findings still fail the build.

## Plugin compatibility verification

`./gradlew :ide-plugin:verifyPlugin` runs the [JetBrains Plugin
Verifier](https://github.com/JetBrains/intellij-plugin-verifier) — it checks
the built plugin for API/binary compatibility against real IDE builds, the same
check the Marketplace performs. Unlike the static analysers above it is **not**
part of `check` / `build`: it is a separate, slow task (it downloads multi-GB
IDE distributions) meant to be run before a release.

The target IDEs are the `pluginVerification` block in
`packages/ide-plugin/build.gradle.kts` — currently both ends of the supported
range, IDEA 2025.3 and 2026.1. Since 2025.3 IntelliJ IDEA ships as a single
unified distribution, so the `IntellijIdea` platform type is used.

The plugin verifies as compatible with both. The only notes are experimental-API
usages from `com.intellij.codeInsight.codeVision.CodeVisionProvider` — the Code
Vision API is marked `@Experimental` by the platform and has no stable
alternative; `verifyPlugin` will flag it if a future IDE changes that API.

## Local workflow

The development loop, top to bottom:

1. **Pick a task.** Features and their status live in
   [FEATURES.md](FEATURES.md). The package boundary is fixed by
   [Separation of responsibilities](#separation-of-responsibilities) — anything
   doable in `ts-plugin` lives there; the IDE plugin is only for what LSP
   cannot deliver.

2. **Write the code** in the right package — TypeScript under
   `packages/ts-plugin/src/`, Kotlin under
   `packages/ide-plugin/src/main/kotlin/fm/sazonov/reatom/`.

3. **Write tests next to the change.** vitest for the analyzer
   (`packages/ts-plugin/test/`, `npm test`); JUnit plus the IntelliJ test
   framework for the IDE plugin (`packages/ide-plugin/src/test/kotlin/`,
   `./gradlew :ide-plugin:test`).

4. **Keep `./gradlew check` green.** It runs detekt + SpotBugs + IDE-plugin
   tests and, via `:ts-plugin:check`, vitest + ESLint. Fix findings or justify
   a config deviation with a comment in the relevant config file.

5. **Run the sandbox** to exercise the change against a real project:

   ```bash
   packages/ide-plugin/scripts/start-sandbox.sh [project-path]   # default: examples/reatom-demo
   packages/ide-plugin/scripts/stop-sandbox.sh
   ```

   The script launches `runIde --args=<project>` detached — **not** raw
   `./gradlew :ide-plugin:runIde`, which lands on the Welcome screen with
   nothing open. The gradle pid is written to
   `packages/ide-plugin/build/.sandbox-ide.pid`, the gradle output to
   `build/sandbox-gradle.log`, the IDE itself logs to
   `build/idea-sandbox/IU-*/log/idea.log`.

6. **Verify live via MCP.** Set the `mcpSteroidDir` Gradle property (e.g. in
   `~/.gradle/gradle.properties`) to a directory holding the
   `mcp-steroid-*.zip` distribution; the sandbox then ships
   [MCP Steroid](https://github.com/jonnyzzz/mcp-steroid). Once a Reatom
   project is open in the sandbox, the snippet below (a `steroid_execute_code`
   payload) returns the node and edge counts of the live reactive graph — the
   definitive "the plugin works" check:

   ```kotlin
   val plugin = PluginManagerCore.getPlugin(PluginId.getId("fm.sazonov.reatom"))!!
   val cl = plugin.classLoader
   val serviceClass = cl.loadClass("fm.sazonov.reatom.analyzer.ReatomGraphService")
   val service = project.getService(serviceClass)!!
   val graph = serviceClass.getMethod("getGraph").invoke(service)
     ?: error("graph not built yet")
   val nodes = graph.javaClass.getMethod("getNodes").invoke(graph) as List<*>
   val edges = graph.javaClass.getMethod("getEdges").invoke(graph) as List<*>
   println("nodes=${nodes.size} edges=${edges.size}")
   ```

   Without MCP the same check is opening a Reatom project in the sandbox and
   looking at Code Lens and gutter icons.

7. **Commit** in English, past tense, no `Co-Authored-By` trailer.

### Reference — handy commands and paths

| Goal | Command |
|---|---|
| Full gate (both plugins) | `./gradlew check` |
| IDE plugin only — compile, detekt, SpotBugs, tests | `./gradlew :ide-plugin:check` |
| Analyzer (ts-plugin) only — build, vitest, ESLint | `cd packages/ts-plugin && npm run build && npm test && npm run lint` |
| Build the distributable plugin zip | `./gradlew :ide-plugin:buildPlugin` → `packages/ide-plugin/build/distributions/ide-plugin-0.0.1.zip` |
| Plugin compatibility against real IDE builds (release-time, slow) | `./gradlew :ide-plugin:verifyPlugin` |

To install the freshly built plugin in OpenIDE / OpenIDE Pro:
`Settings → Plugins → ⚙ → Install Plugin from Disk →` the zip above. The
extracted analyzer bundle cache lives at
`<IDE system path>/reatom-analyzer/analyzer-<hash>.cjs` (on macOS, e.g.
`~/Library/Caches/OpenIDE/OpenIDE-Pro2025.3/reatom-analyzer/`). The file is
named by the bundle's content hash, so a plugin update with a changed analyzer
is picked up automatically — superseded files are cleaned up on the next run,
manual cache invalidation is not needed.

## Working rules

- The project language — documentation, code comments, and commits — is **English**.
- Commit messages are in the past tense (what was done, not what to do).
- Keep `./gradlew check` green — fix findings or justify a config deviation with a comment.
- Do not introduce Pro features or licensing — the product is Free only.
- Do not add Reatom v3 support.

## Useful links

- Reatom v1001 documentation: <https://v1001.reatom.dev>
- Reatom sources: <https://github.com/artalar/reatom>
- TypeScript Language Service Plugins: <https://github.com/microsoft/TypeScript/wiki/Writing-a-Language-Service-Plugin>
- IntelliJ Platform SDK: <https://plugins.jetbrains.com/docs/intellij/>
