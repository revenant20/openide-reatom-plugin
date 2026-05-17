# @openide/reatom-ts-plugin

Анализатор реактивного графа [Reatom v1001](https://v1001.reatom.dev). По
проекту на TypeScript Compiler API строит модель: узлы `atom` / `computed` /
`action` / `effect` и рёбра `read` / `write` / `extend`. На этой модели стоят
Code Lens, gutter-иконки и навигация `reatom-ide-plugin`.

Полная концепция — [docs/features-reatom-plugin.md](../../docs/features-reatom-plugin.md),
дизайн анализатора — [docs/feature-6-analyzer.md](../../docs/feature-6-analyzer.md).

## Состав

- `src/analyzer/` — анализатор: `cli.ts` (one-shot CLI) и `graph.ts`
  (построение модели по готовой `ts.Program`);
- `src/units.ts`, `src/activation.ts` — детекция Reatom-юнитов по резолвингу
  символов фабрик (чужие одноимённые `atom` / `action` отсекаются).

CLI: `node dist/analyzer/cli.js --project path/to/tsconfig.json` — печатает
JSON-модель графа в stdout.

## Бандл для IDE-плагина

`npm run build` помимо `tsc` собирает esbuild'ом самодостаточный бандл
`dist/analyzer/reatom-analyzer.cjs` — весь анализатор плюс TypeScript внутри
одного файла. `reatom-ide-plugin` кладёт этот бандл ресурсом в свой
дистрибутив и запускает сам, поэтому потребителю **не нужно** ставить этот
npm-пакет — достаточно зависимости `@reatom/core` в проекте.

## Разработка

```bash
npm install
npm test --workspace @openide/reatom-ts-plugin
npm run build --workspace @openide/reatom-ts-plugin
```
