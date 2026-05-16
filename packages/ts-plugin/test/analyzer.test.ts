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
  it('собирает узлы atom / computed / action / effect', () => {
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

  it('строит рёбра read/write с привязкой from к объемлющему юниту', () => {
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
    if (!counter || !doubled) throw new Error('узлы не найдены');

    const toCounter = graph.edges.filter((edge) => edge.to === counter.id);
    // doubled читает counter, inc читает counter в аргументе set = 2 чтения.
    expect(toCounter.filter((edge) => edge.kind === 'read')).toHaveLength(2);
    expect(toCounter.filter((edge) => edge.kind === 'write')).toHaveLength(1);

    // Чтение counter внутри computed размечено как исходящее из doubled.
    const readInDoubled = toCounter.find((edge) => edge.from === doubled.id);
    expect(readInDoubled?.kind).toBe('read');
  });

  it('узел несёт extensions и точный диапазон объявления', () => {
    const source =
      `import { atom, withCache } from '@reatom/core';\n` +
      `export const data = atom(0, 'data').extend(withCache());`;
    const { program } = createProgram({ 'model.ts': source });
    const node = buildReatomGraph(ts, program).nodes.find((n) => n.name === 'data');
    if (!node) throw new Error('узел data не найден');
    expect(node.extensions).toEqual(['withCache']);
    expect(source.slice(node.range.start, node.range.end)).toBe('data');
  });
});

describe('CLI анализатора', () => {
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

  it('node cli.js --project tsconfig.json печатает JSON-граф', () => {
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
