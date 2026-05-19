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

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as ts from 'typescript';
import { execFileSync } from 'node:child_process';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildReatomGraph } from '../src/analyzer/graph';
import { createProgram, cleanupFixtures, REATOM_CORE_DTS } from './helpers';

afterAll(cleanupFixtures);

describe('buildReatomGraph', () => {
  it('collects atom / computed / action / effect nodes', () => {
    const { program } = createProgram({
      'model.ts': `
        import { atom, computed, action, effect } from '@reatom/core';
        export const counter = atom(0, 'counter');
        export const doubled = computed(() => counter() * 2, 'doubled');
        export const inc = action(() => counter.set(1), 'inc');
        export const log = effect(() => { counter(); }, 'log');
      `,
    });
    const graph = buildReatomGraph(ts, program);
    expect(graph.schemaVersion).toBe(1);
    expect(graph.nodes.map((node) => [node.name, node.kind]).sort()).toEqual([
      ['counter', 'atom'],
      ['doubled', 'computed'],
      ['inc', 'action'],
      ['log', 'effect'],
    ]);
  });

  it('builds read/write edges with from bound to the enclosing unit', () => {
    const { program } = createProgram({
      'model.ts': `
        import { atom, computed } from '@reatom/core';
        export const counter = atom(0, 'counter');
        export const doubled = computed(() => counter() * 2, 'doubled');
      `,
      'ui.ts': `
        import { counter } from './model';
        export function inc() { counter.set(counter() + 1); }
      `,
    });
    const graph = buildReatomGraph(ts, program);
    const counter = graph.nodes.find((node) => node.name === 'counter');
    const doubled = graph.nodes.find((node) => node.name === 'doubled');
    if (!counter || !doubled) throw new Error('nodes not found');

    const toCounter = graph.edges.filter((edge) => edge.to === counter.id);
    // doubled reads counter, inc reads counter in the set argument = 2 reads.
    expect(toCounter.filter((edge) => edge.kind === 'read')).toHaveLength(2);
    expect(toCounter.filter((edge) => edge.kind === 'write')).toHaveLength(1);

    // The counter read inside computed is marked as originating from doubled.
    const readInDoubled = toCounter.find((edge) => edge.from === doubled.id);
    expect(readInDoubled?.kind).toBe('read');
  });

  it('a node carries extensions and the exact declaration range', () => {
    const source =
      `import { atom, withCache } from '@reatom/core';\n` +
      `export const data = atom(0, 'data').extend(withCache());`;
    const { program } = createProgram({ 'model.ts': source });
    const node = buildReatomGraph(ts, program).nodes.find((n) => n.name === 'data');
    if (!node) throw new Error('node data not found');
    expect(node.extensions).toEqual(['withCache']);
    expect(source.slice(node.range.start, node.range.end)).toBe('data');
  });
});

describe('analyzer CLI', () => {
  let projectDir: string;

  beforeAll(() => {
    projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'reatom-analyzer-'));
    const write = (relative: string, content: string): void => {
      const full = path.join(projectDir, relative);
      fs.mkdirSync(path.dirname(full), { recursive: true });
      fs.writeFileSync(full, content);
    };
    write(
      'node_modules/@reatom/core/package.json',
      JSON.stringify({ name: '@reatom/core', version: '1001.0.0', types: 'index.d.ts' }),
    );
    write('node_modules/@reatom/core/index.d.ts', REATOM_CORE_DTS);
    write(
      'tsconfig.json',
      JSON.stringify({
        compilerOptions: { strict: true, skipLibCheck: true },
        include: ['src'],
      }),
    );
    write(
      'src/model.ts',
      `import { atom } from '@reatom/core';\n` +
        `export const counter = atom(0, 'counter');\n` +
        `export const read = () => counter();`,
    );
  });

  afterAll(() => {
    fs.rmSync(projectDir, { recursive: true, force: true });
  });

  it('node cli.js --project tsconfig.json prints the JSON graph', () => {
    const cli = path.resolve(
      path.dirname(fileURLToPath(import.meta.url)),
      '..',
      'dist',
      'analyzer',
      'cli.js',
    );
    const output = execFileSync(
      process.execPath,
      [cli, '--project', path.join(projectDir, 'tsconfig.json')],
      { encoding: 'utf8' },
    );
    const graph = JSON.parse(output) as {
      schemaVersion: number;
      nodes: Array<{ name: string; kind: string }>;
      edges: Array<{ kind: string }>;
    };
    expect(graph.schemaVersion).toBe(1);
    expect(graph.nodes.some((n) => n.name === 'counter' && n.kind === 'atom')).toBe(true);
    expect(graph.edges.some((e) => e.kind === 'read')).toBe(true);
  });
});

describe('analyzer CLI — solution-style tsconfig', () => {
  let projectDir: string;

  const cliPath = (): string =>
    path.resolve(
      path.dirname(fileURLToPath(import.meta.url)),
      '..',
      'dist',
      'analyzer',
      'cli.js',
    );

  beforeAll(() => {
    projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'reatom-solution-'));
    const write = (relative: string, content: string): void => {
      const full = path.join(projectDir, relative);
      fs.mkdirSync(path.dirname(full), { recursive: true });
      fs.writeFileSync(full, content);
    };
    write(
      'node_modules/@reatom/core/package.json',
      JSON.stringify({ name: '@reatom/core', version: '1001.0.0', types: 'index.d.ts' }),
    );
    write('node_modules/@reatom/core/index.d.ts', REATOM_CORE_DTS);
    // A solution-style root: no files of its own, only references to leaf
    // projects (the layout `tsc --init` and Vite scaffolds produce).
    write(
      'tsconfig.json',
      JSON.stringify({ files: [], references: [{ path: './tsconfig.app.json' }] }),
    );
    write(
      'tsconfig.app.json',
      JSON.stringify({
        compilerOptions: { composite: true, strict: true, skipLibCheck: true },
        include: ['src'],
      }),
    );
    write(
      'src/model.ts',
      `import { atom } from '@reatom/core';\n` +
        `export const counter = atom(0, 'counter');\n` +
        `export const read = () => counter();`,
    );
  });

  afterAll(() => {
    fs.rmSync(projectDir, { recursive: true, force: true });
  });

  it('expands project references instead of returning an empty graph', () => {
    const output = execFileSync(
      process.execPath,
      [cliPath(), '--project', path.join(projectDir, 'tsconfig.json')],
      { encoding: 'utf8' },
    );
    const graph = JSON.parse(output) as {
      nodes: Array<{ name: string; kind: string }>;
      edges: Array<{ kind: string }>;
    };
    expect(graph.nodes.some((n) => n.name === 'counter' && n.kind === 'atom')).toBe(true);
    expect(graph.edges.some((e) => e.kind === 'read')).toBe(true);
  });
});

describe('analyzer CLI — resilient to broken references', () => {
  let projectDir: string;

  const cliPath = (): string =>
    path.resolve(
      path.dirname(fileURLToPath(import.meta.url)),
      '..',
      'dist',
      'analyzer',
      'cli.js',
    );

  beforeAll(() => {
    projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'reatom-broken-ref-'));
    const write = (relative: string, content: string): void => {
      const full = path.join(projectDir, relative);
      fs.mkdirSync(path.dirname(full), { recursive: true });
      fs.writeFileSync(full, content);
    };
    write(
      'node_modules/@reatom/core/package.json',
      JSON.stringify({ name: '@reatom/core', version: '1001.0.0', types: 'index.d.ts' }),
    );
    write('node_modules/@reatom/core/index.d.ts', REATOM_CORE_DTS);
    // The root references three leaves: one good, one malformed, one missing.
    write(
      'tsconfig.json',
      JSON.stringify({
        files: [],
        references: [
          { path: './tsconfig.good.json' },
          { path: './tsconfig.broken.json' },
          { path: './tsconfig.absent.json' },
        ],
      }),
    );
    write(
      'tsconfig.good.json',
      JSON.stringify({
        compilerOptions: { composite: true, strict: true, skipLibCheck: true },
        include: ['src'],
      }),
    );
    write('tsconfig.broken.json', '{ this is not valid json');
    write(
      'src/model.ts',
      `import { atom } from '@reatom/core';\n` +
        `export const counter = atom(0, 'counter');`,
    );
  });

  afterAll(() => {
    fs.rmSync(projectDir, { recursive: true, force: true });
  });

  it('skips unreadable references and still analyzes the good ones', () => {
    const output = execFileSync(
      process.execPath,
      [cliPath(), '--project', path.join(projectDir, 'tsconfig.json')],
      { encoding: 'utf8' },
    );
    const graph = JSON.parse(output) as { nodes: Array<{ name: string }> };
    expect(graph.nodes.some((n) => n.name === 'counter')).toBe(true);
  });
});
