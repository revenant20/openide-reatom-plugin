# @openide/reatom-ts-plugin

A reactive graph analyzer for [Reatom v1001](https://v1001.reatom.dev). It
walks a project with the TypeScript Compiler API and builds a model: `atom` /
`computed` / `action` / `effect` nodes and `read` / `write` / `extend` edges.
On top of this model sit the Code Lens, gutter icons, and navigation of
`reatom-ide-plugin`.

## Contents

- `src/analyzer/` — the analyzer: `cli.ts` (a one-shot CLI) and `graph.ts`
  (building the model from a ready `ts.Program`);
- `src/units.ts`, `src/activation.ts` — detection of Reatom units by resolving
  factory symbols (foreign `atom` / `action` with the same names are filtered out).

CLI: `node dist/analyzer/cli.js --project path/to/tsconfig.json` — prints the
JSON graph model to stdout.

## Bundle for the IDE plugin

In addition to `tsc`, `npm run build` uses esbuild to assemble the
self-contained bundle `dist/analyzer/reatom-analyzer.cjs` — the entire
analyzer plus TypeScript inside a single file. `reatom-ide-plugin` puts this
bundle into its distribution as a resource and runs it itself, so the consumer
**does not need** to install this npm package — a `@reatom/core` dependency in
the project is enough.

## Development

```bash
npm install
npm test --workspace @openide/reatom-ts-plugin
npm run build --workspace @openide/reatom-ts-plugin
```
