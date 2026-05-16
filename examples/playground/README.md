# Playground — проверка inlay hints глазами

Демо-проект, чтобы вживую увидеть подсказки `@openide/reatom-ts-plugin`
(фича 2). Плагин подключён через `tsconfig.json` → `compilerOptions.plugins`.

## Подготовка

Из корня монорепозитория:

```bash
npm install
npm run build --workspace @openide/reatom-ts-plugin
```

`npm install` поставит реальный `@reatom/core` и слинкует плагин как
workspace-зависимость; `build` соберёт `dist/`, который грузит tsserver.

## Что должно быть видно

Открой `src/model.ts` в редакторе с включёнными inlay hints — у объявлений
появятся серые подписи:

```
export const counter ⟦: atom · ↑4 · ↓2⟧ = atom(0, 'counter')
export const doubled ⟦: computed · ↑1 · ↓0⟧ = computed(() => counter() * 2, 'doubled')
export const increment ⟦: action · ↑1 · ↓0⟧ = action(() => { … }, 'increment')
export const counterLogger ⟦: effect · ↑0 · ↓0⟧ = effect(() => { … }, 'counterLogger')
```

## Как открыть

### VS Code

1. Открой каталог `examples/playground` (или весь монорепозиторий).
2. Команда **TypeScript: Select TypeScript Version → Use Workspace Version**
   (плагины из `tsconfig.json` грузит именно tsserver проекта).
3. Включи хотя бы одну штатную TS-настройку inlay hints, иначе хинты
   не запрашиваются вообще — например в `settings.json`:
   ```json
   { "typescript.inlayHints.variableTypes.enabled": true }
   ```

### IntelliJ IDEA / WebStorm / OpenIDE

1. **Settings → Languages & Frameworks → TypeScript** — плагины из
   `tsconfig.json` подхватываются автоматически.
2. **Settings → Editor → Inlay Hints → TypeScript** — включи любую секцию
   (например, Variable types).

## Почему нужна включённая TS-настройка inlay hints

`typescript-language-server` (и встроенная TS-поддержка редакторов) не
вызывает `provideInlayHints`, если выключены **все** штатные TS inlay hints
(гейт `areInlayHintsEnabledForFile`). Наши Reatom-подсказки «едут» на той же
инфраструктуре — поэтому нужна хотя бы одна включённая настройка. Подробнее —
[docs/feature-2-inlay-hints.md](../../docs/feature-2-inlay-hints.md).
