# AGENTS.md

A single file of instructions and context for agents and contributors to the **OpenIDE Reatom Plugin** project. Format — [agents.md](https://agents.md/).

## About the project

Support for the [Reatom v1001](https://v1001.reatom.dev) reactive state manager in OpenIDE and other editors: semantic inspections, reactive graph navigation, snippets, dependency visualization, quick-fixes.

Key decisions:

- We support **only Reatom v1001**. Version v3 is out of scope; the v3 → v1001 migration is a separate class of tasks.
- The product is **free and open** (Free). No licensing, no Pro features.
- The primary target is OpenIDE, but the TS part works in any editor with tsserver.

The full concept and the feature set — [docs/features-reatom-plugin.md](docs/features-reatom-plugin.md).

## Repository structure — a monorepo of two plugins

The repository contains **two plugins** that are developed and versioned together:

### 1. TypeScript plugin — `packages/ts-plugin`

The npm package `@openide/reatom-ts-plugin` — a plugin for `typescript-language-server` (tsserver). It is loaded **inside the tsserver process** and has access to the full typed AST and the Type Checker.

It is responsible for all **semantics**:

- diagnostics (inspections) — `.set` inside `computed`, `await` without `wrap`, `subscribe` leaks, `.extend(...)` ordering conflicts, atom naming;
- code actions (quick-fixes / intentions);
- completion, including snippets in snippet format (analogous to live templates);
- hover / quick documentation.

All of this is delivered to the client over the standard LSP protocol. It works **cross-editor**: OpenIDE, VS Code, WebStorm, Neovim, Helix, Zed — anywhere tsserver is available.

Code Lens via the TS plugin is **not available** (there is no method for it in `ts.LanguageService`), and a categorized Find Usages is not available either (the LSP `references` result is flat). See [docs/feature-2-code-lens.md](docs/feature-2-code-lens.md) and the split of features into class A/B in the concept.

Currently the `ts-plugin` implements the **reactive graph analyzer** — a reusable core (outside tsserver) built on the TypeScript Compiler API. Its self-contained bundle ships inside the IDE plugin (feature 9); the CLI visualization and the toolwindow are built on the same core. The semantics above are still a plan; inlay hints were tried and removed (they duplicated the IDE plugin's Code Lens).

Stack: TypeScript, TypeScript Compiler API.

### 2. IDE plugin — `packages/ide-plugin`

A plugin on the IntelliJ platform (OpenIDE / IntelliJ IDEA). It provides what tsserver and the LSP protocol **cannot** do — native IDE capabilities:

- a **toolwindow** with a static reactive graph of atom dependencies;
- native **gutter icons** (line markers) on `atom` / `computed` / `action` / `effect`;
- bundled **live templates** in the JetBrains format;
- a Reatom support settings page.

In addition, the IDE plugin **ships the graph analyzer inside itself** — a self-contained esbuild bundle (our code plus TypeScript inside a single `.cjs`) — and runs it as a Node process. As a result, the consumer does not need the npm package: a `@reatom/core` dependency in the project is enough. Delivering a future LSP layer into tsserver is an open question (see the concept).

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
├── README.md
├── docs/
│   └── features-reatom-plugin.md   # full concept and feature set
├── packages/
│   ├── ts-plugin/            # @openide/reatom-ts-plugin (TypeScript)
│   └── ide-plugin/           # IDE plugin (Kotlin / Gradle / IntelliJ SDK)
└── templates/                # live templates: reatom.xml, reatom.code-snippets
```

> Status: the graph analyzer (feature 6) is implemented in `packages/ts-plugin`, and native Code Lens / gutter icons / navigation (feature 9) in `packages/ide-plugin`. Inlay hints (feature 2) were tried via the TS LS plugin but removed — they duplicated Code Lens. The `templates/` directory will appear later.

## Working rules

- The project language — documentation, code comments, and commits — is **English**.
- Commit messages are in the past tense (what was done, not what to do).
- Before changing the feature set or the architecture, check against `docs/features-reatom-plugin.md` and keep it up to date.
- Do not introduce Pro features or licensing — the product is Free only.
- Do not add Reatom v3 support.

## Useful links

- Reatom v1001 documentation: <https://v1001.reatom.dev>
- Reatom sources: <https://github.com/artalar/reatom>
- TypeScript Language Service Plugins: <https://github.com/microsoft/TypeScript/wiki/Writing-a-Language-Service-Plugin>
- IntelliJ Platform SDK: <https://plugins.jetbrains.com/docs/intellij/>
