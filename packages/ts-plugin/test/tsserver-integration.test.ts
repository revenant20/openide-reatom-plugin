import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { spawn, execSync, type ChildProcessWithoutNullStreams } from 'node:child_process';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { REATOM_CORE_DTS } from './helpers';

/**
 * Интеграционный тест: плагин грузится в НАСТОЯЩИЙ tsserver через
 * `--globalPlugins` + `--pluginProbeLocations` (тот же механизм, которым его
 * подсунет `typescript-language-server`), а подсказки запрашиваются командой
 * протокола `provideInlayHints`. Проверяет то, что юнит-тесты не покрывают:
 * реальную загрузку плагина, прокси над живым `LanguageService`, доезд
 * хинтов в ответе протокола.
 */

const require = createRequire(import.meta.url);
const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repoRoot = path.resolve(packageRoot, '..', '..');
const tsserverPath = path.join(path.dirname(require.resolve('typescript')), 'tsserver.js');

const MODEL_TS = `import { atom } from '@reatom/core';

export const counter = atom(0, 'counter');

function useCounter(): number {
  return counter() + counter();
}
`;

/**
 * Вычленяет сбалансированные JSON-объекты верхнего уровня. Кадрирование
 * tsserver игнорируем — служебные заголовки (`Content-Length`) и пустые
 * строки просто не начинаются с `{`.
 */
function extractObjects(buffer: string): { objects: unknown[]; rest: string } {
  const objects: unknown[] = [];
  let consumed = 0;
  let i = 0;
  while (i < buffer.length) {
    if (buffer[i] !== '{') {
      i++;
      continue;
    }
    let depth = 0;
    let inString = false;
    let escaped = false;
    let end = -1;
    for (let j = i; j < buffer.length; j++) {
      const ch = buffer[j];
      if (inString) {
        if (escaped) escaped = false;
        else if (ch === '\\') escaped = true;
        else if (ch === '"') inString = false;
      } else if (ch === '"') {
        inString = true;
      } else if (ch === '{') {
        depth++;
      } else if (ch === '}' && --depth === 0) {
        end = j;
        break;
      }
    }
    if (end === -1) break; // объект ещё не дочитан — ждём следующий чанк
    try {
      objects.push(JSON.parse(buffer.slice(i, end + 1)));
    } catch {
      /* не JSON — пропускаем */
    }
    i = end + 1;
    consumed = i;
  }
  return { objects, rest: buffer.slice(consumed) };
}

interface TsServerResponse {
  type: string;
  command: string;
  request_seq: number;
  success?: boolean;
  body?: unknown;
}

/** Тонкая обёртка над процессом tsserver: запрос-ответ по протоколу stdio. */
class TsServer {
  private readonly proc: ChildProcessWithoutNullStreams;
  private seq = 0;
  private buffer = '';
  private readonly waiters = new Map<number, (response: TsServerResponse) => void>();

  constructor(args: string[]) {
    this.proc = spawn(process.execPath, [tsserverPath, ...args], {
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    this.proc.stdout.setEncoding('utf8');
    this.proc.stdout.on('data', (chunk: string) => {
      this.buffer += chunk;
      const { objects, rest } = extractObjects(this.buffer);
      this.buffer = rest;
      for (const object of objects) {
        const message = object as TsServerResponse;
        if (message.type === 'response') {
          const waiter = this.waiters.get(message.request_seq);
          if (waiter) {
            this.waiters.delete(message.request_seq);
            waiter(message);
          }
        }
      }
    });
  }

  private write(command: string, args: unknown): number {
    this.seq += 1;
    this.proc.stdin.write(
      JSON.stringify({ seq: this.seq, type: 'request', command, arguments: args }) + '\n',
    );
    return this.seq;
  }

  /** Команда без ожидания ответа (`open` ответа не присылает). */
  send(command: string, args: unknown): void {
    this.write(command, args);
  }

  /** Команда с ожиданием ответа по `request_seq`. */
  request(command: string, args: unknown, timeoutMs = 30000): Promise<TsServerResponse> {
    const seq = this.write(command, args);
    return new Promise<TsServerResponse>((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiters.delete(seq);
        reject(new Error(`tsserver: тайм-аут ответа на «${command}»`));
      }, timeoutMs);
      this.waiters.set(seq, (response) => {
        clearTimeout(timer);
        resolve(response);
      });
    });
  }

  stop(): void {
    this.proc.kill();
  }
}

let fixtureDir: string;
let server: TsServer;

beforeAll(() => {
  // Плагин грузится из dist/ — собрать перед запуском tsserver.
  execSync('npm run build', { cwd: packageRoot, stdio: 'pipe' });

  fixtureDir = fs.mkdtempSync(path.join(os.tmpdir(), 'reatom-tsserver-'));
  const write = (relative: string, content: string): void => {
    const full = path.join(fixtureDir, relative);
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
    JSON.stringify({ compilerOptions: { strict: true, skipLibCheck: true } }),
  );
  write('model.ts', MODEL_TS);

  server = new TsServer([
    '--globalPlugins',
    '@openide/reatom-ts-plugin',
    '--pluginProbeLocations',
    repoRoot,
    '--disableAutomaticTypingAcquisition',
  ]);
}, 120000);

afterAll(() => {
  server?.stop();
  if (fixtureDir) fs.rmSync(fixtureDir, { recursive: true, force: true });
});

describe('плагин в настоящем tsserver', () => {
  it('provideInlayHints отдаёт Reatom-подсказку с ролью и счётчиками', async () => {
    const modelFile = path.join(fixtureDir, 'model.ts');
    server.send('open', { file: modelFile });

    const response = await server.request('provideInlayHints', {
      file: modelFile,
      start: 0,
      length: MODEL_TS.length,
    });

    expect(response.success).toBe(true);

    const items = (response.body ?? []) as Array<{
      text?: string;
      displayParts?: Array<{ text: string }>;
    }>;
    const labels = items.map(
      (item) =>
        (item.text ?? '') + (item.displayParts ?? []).map((part) => part.text).join(''),
    );

    const atomLabel = labels.find((label) => label.includes(': atom'));
    expect(
      atomLabel,
      `Reatom-подсказка не найдена; tsserver вернул: ${JSON.stringify(labels)}`,
    ).toBeDefined();
    // counter() вызван дважды в useCounter → 2 читателя, 0 писателей.
    expect(atomLabel).toContain('↑2');
    expect(atomLabel).toContain('↓0');
  }, 60000);
});
