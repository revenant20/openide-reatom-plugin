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

import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import * as ts from 'typescript';

/**
 * Минимальный `@reatom/core` для герметичных фикстур: тайпчекер должен
 * резолвить `atom`/`computed`/… в файл под `@reatom/`, иначе детекция роли
 * (она идёт по символу, не по имени) их не примет.
 */
// language=TypeScript
export const REATOM_CORE_DTS = `
export interface Atom<T> {
  (): T;
  set(value: T | ((prev: T) => T)): T;
  subscribe(cb: (value: T) => void): () => void;
  extend(...ext: Array<(target: any) => any>): Atom<T>;
}
export interface Action<P extends any[] = any[], R = any> {
  (...args: P): R;
  subscribe(cb: (payload: R) => void): () => void;
  extend(...ext: Array<(target: any) => any>): Action<P, R>;
}
export declare function atom<T>(initial: T, name?: string): Atom<T>;
export declare function computed<T>(compute: () => T, name?: string): Atom<T>;
export declare function action<P extends any[], R>(fn: (...args: P) => R, name?: string): Action<P, R>;
export declare function effect(fn: () => void, name?: string): { extend(...ext: any[]): unknown };
export declare function wrap<T>(target: T): T;
export declare function withAsync<T>(): (target: T) => T;
export declare function withCache<T>(): (target: T) => T;
export declare function withMemo<T>(): (target: T) => T;
`;

const COMPILER_OPTIONS: ts.CompilerOptions = {
  target: ts.ScriptTarget.ES2020,
  module: ts.ModuleKind.Node16,
  moduleResolution: ts.ModuleResolutionKind.Node16,
  strict: true,
  skipLibCheck: true,
  jsx: ts.JsxEmit.Preserve,
};

const createdDirs: string[] = [];

/** Раскладывает фикстуры во временный каталог с фейковым `@reatom/core`. */
function materialize(files: Record<string, string>): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'reatom-tsplugin-'));
  createdDirs.push(dir);
  const write = (relative: string, content: string): void => {
    const full = path.join(dir, relative);
    fs.mkdirSync(path.dirname(full), { recursive: true });
    fs.writeFileSync(full, content);
  };
  write(
    'node_modules/@reatom/core/package.json',
    JSON.stringify({ name: '@reatom/core', version: '1001.0.0', types: 'index.d.ts' }),
  );
  write('node_modules/@reatom/core/index.d.ts', REATOM_CORE_DTS);
  for (const [relative, content] of Object.entries(files)) write(relative, content);
  return dir;
}

function projectScriptNames(dir: string, files: Record<string, string>): string[] {
  return Object.keys(files)
    .filter((relative) => relative.endsWith('.ts') || relative.endsWith('.tsx'))
    .map((relative) => path.join(dir, relative));
}

/** Проект-фикстура: `ts.Program` плюс резолвер абсолютных путей. */
export interface ProgramFixture {
  program: ts.Program;
  /** Абсолютный путь файла фикстуры по относительному имени. */
  file: (relative: string) => string;
}

/** Собирает `ts.Program` из набора фикстур `{ 'model.ts': '…' }`. */
export function createProgram(files: Record<string, string>): ProgramFixture {
  const dir = materialize(files);
  const program = ts.createProgram({
    rootNames: projectScriptNames(dir, files),
    options: COMPILER_OPTIONS,
  });
  return { program, file: (relative) => path.join(dir, relative) };
}

/** Проект-фикстура поверх `LanguageService` — для теста плагина целиком. */
export interface LanguageServiceFixture {
  service: ts.LanguageService;
  file: (relative: string) => string;
}

/** Собирает `ts.LanguageService` из набора фикстур. */
export function createLanguageService(
  files: Record<string, string>,
): LanguageServiceFixture {
  const dir = materialize(files);
  const scriptNames = projectScriptNames(dir, files);
  const host: ts.LanguageServiceHost = {
    getScriptFileNames: () => scriptNames,
    getScriptVersion: () => '1',
    getScriptSnapshot: (name) => {
      const text = ts.sys.readFile(name);
      return text === undefined ? undefined : ts.ScriptSnapshot.fromString(text);
    },
    getCurrentDirectory: () => dir,
    getCompilationSettings: () => COMPILER_OPTIONS,
    getDefaultLibFileName: (options) => ts.getDefaultLibFilePath(options),
    fileExists: ts.sys.fileExists,
    readFile: ts.sys.readFile,
    readDirectory: ts.sys.readDirectory,
    directoryExists: ts.sys.directoryExists,
    getDirectories: ts.sys.getDirectories,
  };
  return {
    service: ts.createLanguageService(host),
    file: (relative) => path.join(dir, relative),
  };
}

/** Полный диапазон файла — для `provideInlayHints`. */
export function fullSpan(program: ts.Program, fileName: string): ts.TextSpan {
  const sourceFile = program.getSourceFile(fileName);
  return { start: 0, length: sourceFile ? sourceFile.text.length : 0 };
}

/** Удаляет временные каталоги фикстур. Вызывать в `afterAll`. */
export function cleanupFixtures(): void {
  for (const dir of createdDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}
