# Features

Reatom v1001 support for OpenIDE and other editors. The features fall into two
classes; see [AGENTS.md](AGENTS.md) for the architecture behind the split and
the per-package responsibilities.

Status: ✅ implemented · 🚧 planned · ❌ removed.

## Class A — core features

Cross-editor, delivered over standard LSP, implemented in the TS plugin
(`packages/ts-plugin`); they work in any editor with tsserver.

1. **Semantic diagnostics and quick-fixes** 🚧 — inspections on the typed AST:
   `.set(...)` inside `computed`/`effect`, a `Promise` returned from `computed`
   without `wrap`, a leaking `.subscribe(...)`, `.extend(...)` ordering
   conflicts, unnamed or mismatched atom names, `await` without `wrap`.
2. **Inlay hints** ❌ — implemented and then removed: a grey `[atom · ↑N · ↓M]`
   signature duplicated the native Code Lens of feature 9.
3. **Completion snippets** 🚧 — live-template-style snippets (`ratom`,
   `raction`, `rcomp`, `reffect`, `rasync`, `rwith`).
4. **Extended hover / Quick Documentation** 🚧 — enriched hover for the Reatom
   API, with links to v1001.reatom.dev and related `with*` extensions.
5. **`@reatom/react` support** 🚧 — the feature 1 diagnostics plus JSX
   analysis (an atom used in JSX without `useAtom`, `.set(...)` in render).

## Class B — graph analyzer and native IDE features

Not covered by the standard LSP stack; built on the reactive graph analyzer.

6. **Reactive graph analyzer** ✅ `ts-plugin` — the shared core: builds the
   atom/computed/action/effect graph with the TypeScript Compiler API and runs
   as a plain Node process, outside tsserver. Features 7–9 build on it.
7. **CLI graph visualization** 🚧 — `npx @openide/reatom-graph` opens an
   interactive HTML dependency graph in the browser.
8. **Toolwindow with the static reactive graph** 🚧 `ide-plugin` — a native
   panel rendering the whole dependency graph, built statically from the code
   without running the app.
9. **Native Code Lens, gutter icons and navigation** ✅ `ide-plugin` — a
   clickable summary line and gutter icons on `atom`/`computed`/`action`/
   `effect` declarations and usages, with navigation between them.
10. **Bundled live templates** 🚧 `ide-plugin` — the feature 3 snippets as
    JetBrains-format live templates.
