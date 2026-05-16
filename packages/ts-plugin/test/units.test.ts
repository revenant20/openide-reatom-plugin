import { describe, it, expect, afterAll } from 'vitest';
import * as ts from 'typescript';
import { findReatomUnits } from '../src/units';
import { createProgram, cleanupFixtures } from './helpers';

afterAll(cleanupFixtures);

/** Находит юниты в указанном файле фикстуры. */
function unitsOf(files: Record<string, string>, relative = 'model.ts') {
  const { program, file } = createProgram(files);
  const sourceFile = program.getSourceFile(file(relative));
  if (!sourceFile) throw new Error('фикстура не найдена: ' + relative);
  return findReatomUnits(ts, program.getTypeChecker(), sourceFile);
}

describe('findReatomUnits', () => {
  it('распознаёт роль atom / computed / action / effect', () => {
    const units = unitsOf({
      'model.ts': `
        import { atom, computed, action, effect } from '@reatom/core';
        const counter = atom(0, 'counter');
        const doubled = computed(() => counter() * 2, 'doubled');
        const inc = action(() => counter.set(counter() + 1), 'inc');
        const logger = effect(() => { counter(); }, 'logger');
      `,
    });
    expect(units.map((u) => [u.name, u.kind])).toEqual([
      ['counter', 'atom'],
      ['doubled', 'computed'],
      ['inc', 'action'],
      ['logger', 'effect'],
    ]);
  });

  it('не считает юнитом чужую одноимённую функцию atom', () => {
    const units = unitsOf({
      'model.ts': `
        function atom(value: number) { return value; }
        const fake = atom(0);
      `,
    });
    expect(units).toEqual([]);
  });

  it('понимает алиас импорта', () => {
    const units = unitsOf({
      'model.ts': `
        import { atom as makeAtom } from '@reatom/core';
        const counter = makeAtom(0, 'counter');
      `,
    });
    expect(units.map((u) => [u.name, u.kind])).toEqual([['counter', 'atom']]);
  });

  it('собирает with*-расширения из .extend(...)', () => {
    const units = unitsOf({
      'model.ts': `
        import { atom, withAsync, withCache } from '@reatom/core';
        const data = atom(0, 'data').extend(withAsync(), withCache());
      `,
    });
    expect(units).toHaveLength(1);
    expect(units[0].extensions.map((e) => e.name)).toEqual(['withAsync', 'withCache']);
  });

  it('у расширения есть target — навигируемый сегмент', () => {
    const units = unitsOf({
      'model.ts': `
        import { atom, withCache } from '@reatom/core';
        const data = atom(0, 'data').extend(withCache());
      `,
    });
    const ext = units[0].extensions[0];
    expect(ext.name).toBe('withCache');
    expect(ext.target?.fileName).toContain('@reatom/core');
  });

  it('namePosition указывает на конец идентификатора переменной', () => {
    const source = `import { atom } from '@reatom/core';\nconst counter = atom(0);`;
    const units = unitsOf({ 'model.ts': source });
    expect(units[0].namePosition).toBe(source.indexOf('counter') + 'counter'.length);
  });
});
