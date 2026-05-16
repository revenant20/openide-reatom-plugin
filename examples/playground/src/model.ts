import { atom, computed, action, effect } from '@reatom/core';

// Включи inlay hints в редакторе — у каждого объявления ниже появится серая
// подпись: роль сущности и сводка реактивных связей (↑ читатели, ↓ писатели).
// Счётчики считаются по всему проекту, использования из ui.ts тоже учитываются.

/** Базовый атом-состояние. Ожидаемая подпись: `: atom · ↑4 · ↓2`. */
export const counter = atom(0, 'counter');

/** Производное значение. Ожидаемая подпись: `: computed · ↑1 · ↓0`. */
export const doubled = computed(() => counter() * 2, 'doubled');

/** Действие — изменение состояния. Ожидаемая подпись: `: action · ↑1 · ↓0`. */
export const increment = action(() => {
  counter.set(counter() + 1);
}, 'increment');

/** Реактивный side-effect. Ожидаемая подпись: `: effect · ↑0 · ↓0`. */
export const counterLogger = effect(() => {
  console.log('counter =', counter());
}, 'counterLogger');
