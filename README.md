# OpenIDE Reatom Plugin

Поддержка реактивного менеджера состояний [Reatom v1001](https://v1001.reatom.dev) в OpenIDE и других редакторах.

Бесплатный открытый продукт. Поддерживается только Reatom **v1001**.

## Что в репозитории

Это **монорепозиторий из двух плагинов**, которые разрабатываются вместе:

- **`packages/ts-plugin`** — `@openide/reatom-ts-plugin`, плагин для `typescript-language-server`. Вся семантика: инспекции, quick-fixes, completion, сниппеты, hover, расширенный Find Usages, code lens, inlay hints. Работает в любом редакторе с tsserver (OpenIDE, VS Code, WebStorm, Neovim и др.).
- **`packages/ide-plugin`** — IDE-плагин на платформе IntelliJ (OpenIDE / IntelliJ IDEA). Нативные возможности IDE поверх TS plugin: toolwindow со статическим реактивным графом, gutter-иконки, live templates. Также бандлит и доставляет TS plugin в tsserver, чтобы пользователю не нужно было ставить npm-пакет вручную.

## Документация

- [docs/features-reatom-plugin.md](docs/features-reatom-plugin.md) — полная концепция, архитектура и состав фичей.
- [AGENTS.md](AGENTS.md) — описание проекта и правила работы для контрибьюторов (формат [agents.md](https://agents.md/)).

## Статус

В разработке. Реализовано:

- **`packages/ts-plugin`** — фича 2 (inlay hints) и фича 6 (анализатор реактивного графа);
- **`packages/ide-plugin`** — фича 9 (нативный Code Lens и gutter-иконки).

Остальные фичи — в [концепции](docs/features-reatom-plugin.md).

## Лицензия

[Apache License 2.0](LICENSE). © 2026 Fedor Sazonov.