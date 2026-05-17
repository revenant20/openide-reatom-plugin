/*
 * Copyright 2026 Fedor Sazonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { describe, it, expect, afterAll } from 'vitest';
import * as ts from 'typescript';
import { findReatomUnits } from '../src/units';
import { createProgram, cleanupFixtures } from './helpers';

afterAll(cleanupFixtures);

/** Finds units in the given fixture file. */
function unitsOf(files: Record<string, string>, relative = 'model.ts') {
  const { program, file } = createProgram(files);
  const sourceFile = program.getSourceFile(file(relative));
  if (!sourceFile) throw new Error('fixture not found: ' + relative);
  return findReatomUnits(ts, program.getTypeChecker(), sourceFile);
}

describe('findReatomUnits', () => {
  it('recognizes the atom / computed / action / effect role', () => {
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

  it('does not treat an unrelated function named atom as a unit', () => {
    const units = unitsOf({
      'model.ts': `
        function atom(value: number) { return value; }
        const fake = atom(0);
      `,
    });
    expect(units).toEqual([]);
  });

  it('understands an import alias', () => {
    const units = unitsOf({
      'model.ts': `
        import { atom as makeAtom } from '@reatom/core';
        const counter = makeAtom(0, 'counter');
      `,
    });
    expect(units.map((u) => [u.name, u.kind])).toEqual([['counter', 'atom']]);
  });

  it('collects with* extensions from .extend(...)', () => {
    const units = unitsOf({
      'model.ts': `
        import { atom, withAsync, withCache } from '@reatom/core';
        const data = atom(0, 'data').extend(withAsync(), withCache());
      `,
    });
    expect(units).toHaveLength(1);
    expect(units[0].extensions.map((e) => e.name)).toEqual(['withAsync', 'withCache']);
  });
});
