import { counter, doubled, increment } from './model';

// Использования из этого файла попадают в счётчики ↑/↓ подписей в model.ts —
// плагин считает реактивные связи по всему проекту, а не по одному файлу.

export function renderCounter(): string {
  return `${counter()} (×2 = ${doubled()})`;
}

export function handleIncrementClick(): void {
  increment();
}

export function reset(): void {
  counter.set(0);
}
