# OpenIDE Reatom Plugin

[English](README.md)

Поддержка реактивного менеджера состояний [Reatom v1001](https://v1001.reatom.dev)
в OpenIDE и IntelliJ IDEA — навигация по реактивному графу прямо в редакторе.

Открытый продукт под лицензией Apache 2.0. Поддерживается только Reatom **v1001**.

## Что умеет

Для объявлений `atom` / `computed` / `action` / `effect` в проекте IDE-плагин даёт:

- **Code Lens** — строка-сводка над объявлением: роль юнита и счётчики
  реактивных связей (`atom · ↑4 · ↓2`);
- **gutter-иконки** — на объявлении отдельные иконки читателей и писателей
  (клик открывает попап с использованиями); на строке использования — иконка
  перехода к объявлению;
- **навигацию** «использование ↔ объявление», в том числе между файлами.

Связи берутся из статического анализа кода — без запуска приложения.

## Структура репозитория

Монорепозиторий из двух пакетов с единым Gradle-билдом:

- **`packages/ts-plugin`** (`@openide/reatom-ts-plugin`) — анализатор
  реактивного графа на TypeScript Compiler API. Парсит проект и строит
  JSON-модель: узлы `atom` / `computed` / `action` / `effect` и рёбра
  `read` / `write` / `extend`. Собирается esbuild'ом в самодостаточный бандл.
- **`packages/ide-plugin`** — IDE-плагин на платформе IntelliJ (Kotlin).
  Рисует Code Lens, gutter-иконки и навигацию. **Возит анализатор в себе** —
  потребителю не нужен npm-пакет, достаточно зависимости `@reatom/core`
  в проекте.

## Сборка

Нужен **JDK 21**. Репозиторий — единый Gradle multi-project, сборка из корня:

```bash
# собрать и проверить всё: анализатор, IDE-плагин, тесты
./gradlew build

# дистрибутив IDE-плагина → packages/ide-plugin/build/distributions/*.zip
./gradlew :ide-plugin:buildPlugin
```

Платформа IntelliJ IDEA 2025.3 и Node для сборки анализатора скачиваются
автоматически. Песочница IDE с установленным плагином —
`packages/ide-plugin/scripts/start-sandbox.sh`. Плагин пока не опубликован
в маркетплейсе — ставится из собранного дистрибутива.

## Статус

Ранняя разработка. Реализовано:

- **анализатор реактивного графа** (`ts-plugin`);
- **нативные Code Lens, gutter-иконки и навигация** (`ide-plugin`).

Полная концепция и ещё не реализованные фичи (диагностики, quick-fixes,
completion-сниппеты, toolwindow с графом и др.) — в
[docs/features-reatom-plugin.md](docs/features-reatom-plugin.md).

## Документация

- [docs/features-reatom-plugin.md](docs/features-reatom-plugin.md) — концепция,
  архитектура и состав фичей;
- [AGENTS.md](AGENTS.md) — описание проекта и правила работы для контрибьюторов
  (формат [agents.md](https://agents.md/)).

## Лицензия

[Apache License 2.0](LICENSE). © 2026 Fedor Sazonov.
