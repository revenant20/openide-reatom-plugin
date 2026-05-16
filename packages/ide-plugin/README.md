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

Песочницу запускает соседний проект `openide-mcp`: его `build.gradle.kts`
подключает наш плагин как `localPlugin` (`reatomJar`) — тем же механизмом,
которым там подключены MCP Steroid (`mcpSteroidJar`) и `php-plugin`
(`openphpJar`). Поэтому в песочнице вместе с reatom-ide-plugin доступны
MCP Steroid и MCP Server — IDE можно управлять AI-агентом.

## Зависимость от анализатора

Плагин в рантайме ищет CLI анализатора по пути
`node_modules/@openide/reatom-ts-plugin/dist/analyzer/cli.js` — вверх от
каталога проекта. Перед использованием анализатор должен быть собран:

```bash
npm run build --workspace @openide/reatom-ts-plugin
```
