# @openide/reatom-ts-plugin

TypeScript Language Service plugin для [Reatom v1001](https://v1001.reatom.dev).
Загружается внутрь `tsserver` и добавляет кросс-редакторную семантику Reatom
поверх стандартного LSP-стека.

Полная концепция — [docs/features-reatom-plugin.md](../../docs/features-reatom-plugin.md).

## Реализовано

### Фича 2 — Inlay hints

Серые инлайновые подписи у объявлений `atom` / `computed` / `action` / `effect`:

```
const counter ⟦: atom · ↑3 · ↓1⟧ = atom(0, 'counter')
const data    ⟦: atom · ↑0 · ↓0 · ⤴withCache⟧ = atom(0, 'data').extend(withCache())
```

- **роль сущности** — `atom` / `computed` / `action` / `effect`, по резолвингу
  символа фабрики (чужие одноимённые функции отсекаются);
- **сводка связей** — `↑N` читатели (`unit()`), `↓N` писатели (`unit.set(...)`),
  подсчёт по всему проекту с кэшем по `Program`;
- **`with*`-расширения** — навигируемые сегменты подписи (Ctrl/Cmd-click ведёт
  к определению расширения).

> ⚠️ Видимость у пользователя завязана на гейт `areInlayHintsEnabledForFile`
> в `typescript-language-server`: если выключены **все** штатные TS inlay hints,
> `provideInlayHints` вообще не вызывается. См. `docs/feature-2-inlay-hints.md`.

## Разработка

```bash
npm install
npm test --workspace @openide/reatom-ts-plugin
npm run build --workspace @openide/reatom-ts-plugin
```
