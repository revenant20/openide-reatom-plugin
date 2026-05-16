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

Открой `src/model.ts` — у объявлений появятся серые подписи:

```
export const counter ⟦: atom · ↑4 · ↓2⟧ = atom(0, 'counter')
export const doubled ⟦: computed · ↑1 · ↓0⟧ = computed(() => counter() * 2, 'doubled')
export const increment ⟦: action · ↑1 · ↓0⟧ = action(() => { … }, 'increment')
export const counterLogger ⟦: effect · ↑0 · ↓0⟧ = effect(() => { … }, 'counterLogger')
```

## Как открыть

### VS Code

1. Открой каталог `examples/playground`.
2. **Переключись на рабочую версию TypeScript** (обязательно — см. ниже):
   открой `src/model.ts`, кликни версию TS в статус-баре справа внизу →
   **Select TypeScript Version… → Use Workspace Version**.
3. `Cmd+Shift+P → Developer: Reload Window`, подожди старт tsserver.

Гейт inlay hints и `typescript.tsdk` уже прописаны в `.vscode/settings.json` —
отдельно их настраивать не нужно.

### IntelliJ IDEA / WebStorm / OpenIDE

1. **Settings → Languages & Frameworks → TypeScript** — плагины из
   `tsconfig.json` подхватываются автоматически.
2. **Settings → Editor → Inlay Hints → TypeScript** — включи любую секцию.

## Почему нужна рабочая версия TypeScript (VS Code)

tsserver ищет плагины из `tsconfig.json` в `node_modules` **рядом со своей
установкой**, а не в каталоге проекта. Встроенный в VS Code TypeScript лежит
внутри `VS Code.app`, поэтому плагин из `node_modules` монорепозитория он не
находит. `typescript.tsdk` в `.vscode/settings.json` указывает на TypeScript
из репозитория — тогда tsserver ищет плагины в `<repo>/node_modules`, где
плагин и слинкован. Но переключиться на эту версию VS Code требует один раз
вручную (шаг 2) — из соображений безопасности.

Это ровно та причина, по которой штатная дистрибуция плагина — bundle в
IDE-плагин через `globalPlugins`, а не подключение через `tsconfig`
(см. [docs/features-reatom-plugin.md](../../docs/features-reatom-plugin.md)).

## Почему нужен включённый inlay hints

Редактор не вызывает `provideInlayHints`, если выключены **все** штатные TS
inlay hints. Reatom-подсказки едут на той же инфраструктуре. В
`.vscode/settings.json` для этого включён `typescript.inlayHints.enumMemberValues`
— он открывает гейт и не добавляет своих подсказок (в playground нет enum'ов).
Подробнее — [docs/feature-2-inlay-hints.md](../../docs/feature-2-inlay-hints.md).
