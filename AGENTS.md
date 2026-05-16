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
- hover / quick documentation;
- inlay hints — роль сущности (`[atom]`/`[computed]`/…) и навигируемая сводка реактивных связей.

Всё это отдаётся клиенту по стандартному LSP-протоколу. Работает **кросс-редакторно**: OpenIDE, VS Code, WebStorm, Neovim, Helix, Zed — везде, где есть tsserver.

Code lens через TS plugin **недоступен** (нет метода в `ts.LanguageService`), категоризированный Find Usages — тоже (LSP `references` плоский). См. [docs/feature-2-code-lens.md](docs/feature-2-code-lens.md) и деление фич на класс A/B в концепции.

Дополнительно ts-plugin содержит **анализатор реактивного графа** — переиспользуемое ядро (вне tsserver), на котором стоят CLI-визуализация и toolwindow IDE-плагина.

Стек: TypeScript, TypeScript Compiler API.

### 2. IDE-плагин — `packages/ide-plugin`

Плагин на платформе IntelliJ (OpenIDE / IntelliJ IDEA). Даёт то, что tsserver и LSP-протокол сделать **не могут** — нативные возможности IDE:

- **toolwindow** со статическим реактивным графом зависимостей атомов;
- нативные **gutter-иконки** (line markers) на `atom` / `computed` / `action` / `effect`;
- bundled **live templates** для JetBrains-формата;
- страница настроек поддержки Reatom.

Кроме того, IDE-плагин **бандлит TS plugin внутри себя** и регистрирует его через `initializationOptions.plugins` при инициализации `typescript-language-server` (тот сам транслирует это в `globalPlugins` / `pluginProbeLocations` tsserver) — чтобы пользователю не нужно было ставить npm-пакет и править `tsconfig.json`. Точный способ интеграции с frontend-плагином OpenIDE — открытый вопрос (см. раздел в концепции).

Стек: Kotlin, Gradle, IntelliJ Platform SDK, Gradle IntelliJ Plugin.

### Разделение ответственности

| Слой | TS plugin | IDE-плагин |
|---|---|---|
| Семантика (инспекции, quick-fix, completion, hover) | ✅ | — |
| Кросс-редакторность | ✅ (любой tsserver) | ❌ (только IntelliJ-платформа) |
| Toolwindow с графом, нативные gutter-иконки | ❌ | ✅ |
| Доставка TS plugin в tsserver без npm | — | ✅ |

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

> На текущем этапе репозиторий содержит только документацию. Каталоги `packages/` и `templates/` появятся по мере старта разработки.

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