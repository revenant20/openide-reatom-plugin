/**
 * Точка входа для tsserver. Плагин подключается через `export =`: tsserver
 * делает `require()` модуля и зовёт результат как функцию-фабрику.
 *
 * Сама логика — в `./plugin` (там же её удобно импортировать в тестах
 * обычным ESM-импортом, без интеропа вокруг `export =`).
 */
import { reatomTsPlugin } from './plugin';

export = reatomTsPlugin;
