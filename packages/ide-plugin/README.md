# reatom-ide-plugin

IDE-плагин на платформе IntelliJ (Kotlin) — нативная часть поддержки Reatom
для OpenIDE / IntelliJ IDEA. Реализует **фичу 9** из
[концепции](../../docs/features-reatom-plugin.md):

- **Code Lens** — кликабельная строка-сводка `atom · ↑N · ↓M · ⤴with*` над
  объявлениями `atom` / `computed` / `action` / `effect`
  (`com.intellij.codeInsight.codeVisionProvider`);
- **gutter-иконки** на тех же строках с тултипом-сводкой реактивных связей
  (markup-модель редактора — не `LineMarkerProvider`, тот PSI-зависим, а
  TS-PSI в OpenIDE нет).

Данные берутся из статического реактивного графа: плагин запускает анализатор
`@openide/reatom-ts-plugin` (фича 6) отдельным Node-процессом и читает
JSON-модель (вариант 2a гибридной архитектуры).

## Архитектура

| Компонент | Роль |
|---|---|
| `model/` | модель графа (узлы/рёбра) + чистые операции (сводки, подписи) |
| `analyzer/ReatomAnalyzerLocator` | находит `node`, CLI анализатора, `tsconfig.json` |
| `analyzer/ReatomGraphService` | проектный сервис: запускает анализатор, хранит граф |
| `codevision/ReatomCodeVisionProvider` | Code Lens по offset'ам графа |
| `gutter/ReatomGutterRenderer` | gutter-иконки через `RangeHighlighter` |

## Сборка и проверка

Требуется **JDK 21**. На этой машине — `liberica-21`:

```bash
export JAVA_HOME=/Users/sazonovfm/Library/Java/JavaVirtualMachines/liberica-21.0.10

# собрать дистрибутив плагина → build/distributions/reatom-ide-plugin-*.zip
./gradlew buildPlugin

# прогнать тесты (модель + рендеринг Code Lens / gutter на BasePlatformTestCase)
./gradlew test
```

Платформа IntelliJ IDEA 2025.3 (build 253) скачивается Gradle автоматически.

## Песочница

`./scripts/start-sandbox.sh [путь-к-проекту]` поднимает песочницу IDE с
плагином (по умолчанию открывает `../../../reatom-playground`),
`./scripts/stop-sandbox.sh` — останавливает.

В сборку песочницы через `localPlugin` подключён **MCP Steroid** — берётся
из локальной сборки `../../../mcp-steroid/ij-plugin/build/distributions/`
(путь переопределяется `-PmcpSteroidJar=...`). Так в песочнице, помимо
reatom-ide-plugin, доступно AI-управление IDE. Если ZIP MCP Steroid не
собран — песочница поднимается без него, `buildPlugin`/`test` не страдают.
Механизм взят из `openide-mcp` как образец; зависимости от самого
`openide-mcp` нет.

## Встроенный анализатор

Плагин **возит анализатор в себе** — потребителю не нужен npm-пакет
`@openide/reatom-ts-plugin`. `ts-plugin` собирает анализатор в один
самодостаточный `.cjs` (наш код + TypeScript внутри, esbuild), `build.gradle.kts`
кладёт его ресурсом в jar (`analyzer/reatom-analyzer.cjs`), а
`ReatomAnalyzerLocator` распаковывает бандл в системный каталог IDE и
запускает `node` на нём.

Поэтому перед сборкой IDE-плагина нужно собрать `ts-plugin` — иначе бандла
не будет (`buildPlugin` это переживёт, но анализатор не заработает):

```bash
npm run build --workspace @openide/reatom-ts-plugin
```

Анализатор запускается только если проект **реально использует Reatom** —
зависит от `@reatom/core` (см. `ReatomAnalyzerLocator.usesReatom`).
