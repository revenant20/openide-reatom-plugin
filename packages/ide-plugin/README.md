# reatom-ide-plugin

An IDE plugin on the IntelliJ platform (Kotlin) — the native part of Reatom
support for OpenIDE / IntelliJ IDEA. It implements **feature 9** from the
[concept](../../docs/features-reatom-plugin.md):

- **Code Lens** — a clickable summary line `atom · ↑N · ↓M · ⤴with*` above
  `atom` / `computed` / `action` / `effect` declarations
  (`com.intellij.codeInsight.codeVisionProvider`);
- **gutter icons** on the same lines with a tooltip summary of reactive
  connections (the editor markup model — not `LineMarkerProvider`, which is
  PSI-dependent, and there is no TS PSI in OpenIDE).

The data comes from the static reactive graph: the plugin runs the
`@openide/reatom-ts-plugin` analyzer (feature 6) as a separate Node process
and reads the JSON model (variant 2a of the hybrid architecture).

## Architecture

| Component | Role |
|---|---|
| `model/` | graph model (nodes/edges) + pure operations (summaries, signatures) |
| `analyzer/ReatomAnalyzerLocator` | locates `node`, the analyzer CLI, and `tsconfig.json` |
| `analyzer/ReatomGraphService` | a project service: runs the analyzer, holds the graph |
| `codevision/ReatomCodeVisionProvider` | Code Lens by graph offsets |
| `gutter/ReatomGutterRenderer` | gutter icons via `RangeHighlighter` |

## Building and verifying

**JDK 21** is required (point `JAVA_HOME` at it). The repository is a single
Gradle multi-project; the build runs **from the root**:

```bash
# plugin distribution → packages/ide-plugin/build/distributions/reatom-ide-plugin-*.zip
./gradlew :ide-plugin:buildPlugin

# IDE plugin tests (model + Code Lens / gutter rendering on BasePlatformTestCase)
./gradlew :ide-plugin:test

# build and verify the whole repository — :ide-plugin and the :ts-plugin analyzer
./gradlew build
```

`:ide-plugin:buildPlugin` builds the analyzer bundle from the `:ts-plugin` subproject itself.
The IntelliJ IDEA 2025.3 platform (build 253) is downloaded by Gradle automatically.

## Sandbox

`./scripts/start-sandbox.sh [path-to-project]` brings up an IDE sandbox with
the plugin (by default it opens `../../../reatom-playground`);
`./scripts/stop-sandbox.sh` stops it.

The sandbox build includes **MCP Steroid** via `localPlugin` — it is taken
from the local build at `../../../mcp-steroid/ij-plugin/build/distributions/`
(the path is overridden with `-PmcpSteroidJar=...`). This way, in addition to
reatom-ide-plugin, AI-driven IDE control is available in the sandbox. If the
MCP Steroid ZIP is not built, the sandbox comes up without it, and
`buildPlugin`/`test` are unaffected. The mechanism is borrowed from
`openide-mcp` as a template; there is no dependency on `openide-mcp` itself.

## Built-in analyzer

The plugin **ships the analyzer inside itself** — the consumer does not need
the `@openide/reatom-ts-plugin` npm package. The `:ts-plugin` subproject
builds the analyzer into a single self-contained `.cjs` (our code plus
TypeScript inside, esbuild); the IDE plugin's `processResources` depends on
the `:ts-plugin:buildAnalyzer` task and puts the bundle into the jar as a
resource (`analyzer/reatom-analyzer.cjs`), while `ReatomAnalyzerLocator`
extracts it into the IDE system directory and runs `node` on it.

There is no need to build `ts-plugin` separately — `:ide-plugin:buildPlugin`
pulls in `:ts-plugin:buildAnalyzer` itself.

The analyzer runs only if the project **actually uses Reatom** — that is, it
depends on `@reatom/core` (see `ReatomAnalyzerLocator.usesReatom`).
