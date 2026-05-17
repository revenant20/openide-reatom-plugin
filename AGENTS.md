# AGENTS.md

Единый файл инструкций и контекста для агентов и контрибьюторов проекта **OpenIDE Reatom Plugin**. Формат — [agents.md](https://agents.md/).

## О проекте

Поддержка реактивного менеджера состояний [Reatom v1001](https://v1001.reatom.dev) в OpenIDE и других редакторах: семантические инспекции, навигация по реактивному графу, сниппеты, визуализация зависимостей, quick-fixes.

Ключевые решения:

- Поддерживаем **только Reatom v1001**. Версия v3 вне скоупа, миграция v3 → v1001 — отдельный класс задач.
- Продукт **бесплатный и открытый** (Free). Без лицензирования и Pro-фичей.
- Основной таргет — OpenIDE, но TS-часть работает в любом редакторе с tsserver.

Полная концепция и состав фичей — [docs/features-reatom-plugin.md](docs/features-reatom-plugin.md).

## Структура репозитория — монорепозиторий из двух плагинов

Репозиторий содержит **два плагина**, которые разрабатываются и версионируются вместе:

### 1. TypeScript plugin — `packages/ts-plugin`

npm-пакет `@openide/reatom-ts-plugin` — плагин для `typescript-language-server` (tsserver). Загружается **внутрь tsserver-процесса**, имеет доступ к полному типизированному AST и Type Checker.

Отвечает за всю **семантику**:

- диагностики (инспекции) — `.set` в `computed`, `await` без `wrap`, утечки `subscribe`, конфликты порядка `.extend(...)`, именование атомов;
- code actions (quick-fixes / intentions);
- completion, включая сниппеты в snippet-формате (аналог live templates);
- hover / quick documentation.

Всё это отдаётся клиенту по стандартному LSP-протоколу. Работает **кросс-редакторно**: OpenIDE, VS Code, WebStorm, Neovim, Helix, Zed — везде, где есть tsserver.

Code lens через TS plugin **недоступен** (нет метода в `ts.LanguageService`), категоризированный Find Usages — тоже (LSP `references` плоский). См. [docs/feature-2-code-lens.md](docs/feature-2-code-lens.md) и деление фич на класс A/B в концепции.

Сейчас в `ts-plugin` реализован **анализатор реактивного графа** — переиспользуемое ядро (вне tsserver) на TypeScript Compiler API. Его самодостаточный бандл возит в себе IDE-плагин (фича 9); на том же ядре стоят CLI-визуализация и toolwindow. Семантика выше — пока план; inlay hints пробовали и убрали (дублировали Code Lens IDE-плагина).

Стек: TypeScript, TypeScript Compiler API.

### 2. IDE-плагин — `packages/ide-plugin`

Плагин на платформе IntelliJ (OpenIDE / IntelliJ IDEA). Даёт то, что tsserver и LSP-протокол сделать **не могут** — нативные возможности IDE:

- **toolwindow** со статическим реактивным графом зависимостей атомов;
- нативные **gutter-иконки** (line markers) на `atom` / `computed` / `action` / `effect`;
- bundled **live templates** для JetBrains-формата;
- страница настроек поддержки Reatom.

Кроме того, IDE-плагин **возит в себе анализатор графа** — самодостаточный esbuild-бандл (наш код + TypeScript внутри одного `.cjs`) — и запускает его Node-процессом. Поэтому потребителю не нужен npm-пакет: достаточно зависимости `@reatom/core` в проекте. Доставка будущего LSP-слоя в tsserver — открытый вопрос (см. концепцию).

Стек: Kotlin, Gradle, IntelliJ Platform SDK, Gradle IntelliJ Plugin.

### Разделение ответственности

| Слой | TS plugin | IDE-плагин |
|---|---|---|
| Семантика (инспекции, quick-fix, completion, hover) | ✅ | — |
| Кросс-редакторность | ✅ (любой tsserver) | ❌ (только IntelliJ-платформа) |
| Toolwindow с графом, нативные gutter-иконки | ❌ | ✅ |
| Доставка анализатора без npm-пакета у потребителя | — | ✅ |

Правило: всё, что можно сделать в TS plugin, делается в TS plugin (ради кросс-редакторности). IDE-плагин — только для нативных IDE-фич, которые принципиально невозможны через LSP.

## Структура каталогов

```
openide-reatom-plugin/
├── AGENTS.md                 # этот файл
├── CLAUDE.md                 # точка входа, ведёт в AGENTS.md
├── README.md
├── docs/
│   └── features-reatom-plugin.md   # полная концепция и состав фичей
├── packages/
│   ├── ts-plugin/            # @openide/reatom-ts-plugin (TypeScript)
│   └── ide-plugin/           # IDE-плагин (Kotlin / Gradle / IntelliJ SDK)
└── templates/                # live templates: reatom.xml, reatom.code-snippets
```

> Статус: реализованы анализатор графа (фича 6) в `packages/ts-plugin` и нативные Code Lens / gutter-иконки / навигация (фича 9) в `packages/ide-plugin`. Inlay hints (фича 2) пробовали через TS LS-плагин, но убрали — дублировали Code Lens. Каталог `templates/` появится позже.

## Правила работы

- Язык общения, документации и коммитов — **русский**.
- Сообщения коммитов — в прошедшем времени (что сделал, а не что сделать).
- Перед изменением состава фичей или архитектуры — сверяться с `docs/features-reatom-plugin.md` и обновлять его.
- Не вводить Pro-фичи и лицензирование — продукт только Free.
- Не добавлять поддержку Reatom v3.

## Полезные ссылки

- Документация Reatom v1001: <https://v1001.reatom.dev>
- Исходники Reatom: <https://github.com/artalar/reatom>
- TypeScript Language Service Plugins: <https://github.com/microsoft/TypeScript/wiki/Writing-a-Language-Service-Plugin>
- IntelliJ Platform SDK: <https://plugins.jetbrains.com/docs/intellij/>