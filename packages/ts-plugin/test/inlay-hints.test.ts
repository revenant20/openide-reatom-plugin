import { describe, it, expect, afterAll } from 'vitest';
import * as ts from 'typescript';
import { computeReatomInlayHints } from '../src/inlay-hints';
import { defaultConfig } from '../src/config';
import { createProgram, fullSpan, cleanupFixtures } from './helpers';

afterAll(cleanupFixtures);

/** Склеивает текст сегментов подписи hint'а. */
function labelOf(hint: ts.InlayHint): string {
  return (hint.displayParts ?? []).map((part) => part.text).join('');
}

describe('computeReatomInlayHints', () => {
  it('подпись с ролью для каждого юнита', () => {
    const { program, file } = createProgram({
      'model.ts': `
        import { atom, computed } from '@reatom/core';
        const counter = atom(0, 'counter');
        const doubled = computed(() => counter() * 2, 'doubled');
      `,
    });
    const name = file('model.ts');
    const hints = computeReatomInlayHints(
      ts, program, name, fullSpan(program, name), defaultConfig,
    );
    expect(hints).toHaveLength(2);
    expect(labelOf(hints[0])).toContain(': atom');
    expect(labelOf(hints[1])).toContain(': computed');
  });

  it('считает читателей и писателей по всему проекту', () => {
    const { program, file } = createProgram({
      'model.ts': `
        import { atom } from '@reatom/core';
        export const counter = atom(0, 'counter');
      `,
      'ui.ts': `
        import { counter } from './model';
        const a = counter();
        const b = counter();
        function inc() { counter.set(counter() + 1); }
      `,
    });
    const name = file('model.ts');
    const hints = computeReatomInlayHints(
      ts, program, name, fullSpan(program, name), defaultConfig,
    );
    expect(hints).toHaveLength(1);
    // ui.ts: counter() ×2 + counter() в аргументе set = 3 чтения; counter.set ×1.
    expect(labelOf(hints[0])).toContain('↑3');
    expect(labelOf(hints[0])).toContain('↓1');
  });

  it('юниты вне span не попадают в результат', () => {
    const source = `import { atom } from '@reatom/core';\nconst counter = atom(0, 'counter');`;
    const { program, file } = createProgram({ 'model.ts': source });
    const name = file('model.ts');
    const beforeDeclaration: ts.TextSpan = { start: 0, length: 10 };
    expect(
      computeReatomInlayHints(ts, program, name, beforeDeclaration, defaultConfig),
    ).toEqual([]);
  });

  it('не-Reatom файл не даёт подсказок', () => {
    const { program, file } = createProgram({
      'plain.ts': `function atom(v: number) { return v; }\nconst y = atom(2);`,
    });
    const name = file('plain.ts');
    expect(
      computeReatomInlayHints(ts, program, name, fullSpan(program, name), defaultConfig),
    ).toEqual([]);
  });

  it('showCounts:false убирает счётчики из подписи', () => {
    const { program, file } = createProgram({
      'model.ts': `import { atom } from '@reatom/core';\nconst counter = atom(0, 'counter');`,
    });
    const name = file('model.ts');
    const hints = computeReatomInlayHints(ts, program, name, fullSpan(program, name), {
      ...defaultConfig,
      showCounts: false,
    });
    expect(labelOf(hints[0])).toBe(': atom');
  });

  it('enabled:false полностью отключает фичу', () => {
    const { program, file } = createProgram({
      'model.ts': `import { atom } from '@reatom/core';\nconst counter = atom(0);`,
    });
    const name = file('model.ts');
    expect(
      computeReatomInlayHints(ts, program, name, fullSpan(program, name), {
        ...defaultConfig,
        enabled: false,
      }),
    ).toEqual([]);
  });

  it('расширение попадает в подпись навигируемым сегментом', () => {
    const { program, file } = createProgram({
      'model.ts':
        `import { atom, withCache } from '@reatom/core';\n` +
        `const data = atom(0, 'data').extend(withCache());`,
    });
    const name = file('model.ts');
    const hints = computeReatomInlayHints(
      ts, program, name, fullSpan(program, name), defaultConfig,
    );
    const navigable = (hints[0].displayParts ?? []).find((p) =>
      p.text.includes('withCache'),
    );
    expect(navigable).toBeDefined();
    expect(navigable?.file).toContain('@reatom/core');
    expect(labelOf(hints[0])).toContain('⤴withCache');
  });

  it('hint спозиционирован после имени, kind = Type', () => {
    const source = `import { atom } from '@reatom/core';\nconst counter = atom(0);`;
    const { program, file } = createProgram({ 'model.ts': source });
    const name = file('model.ts');
    const hints = computeReatomInlayHints(
      ts, program, name, fullSpan(program, name), defaultConfig,
    );
    expect(hints[0].position).toBe(source.indexOf('counter') + 'counter'.length);
    expect(hints[0].kind).toBe(ts.InlayHintKind.Type);
    expect(hints[0].whitespaceBefore).toBe(true);
  });
});
