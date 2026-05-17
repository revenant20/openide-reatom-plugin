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
 * A minimal `@reatom/core` for hermetic fixtures: the TypeChecker must
 * resolve `atom`/`computed`/… to a file under `@reatom/`, otherwise role
 * detection (which goes by symbol, not by name) won't accept them.
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

/** Lays out the fixtures into a temporary directory with a fake `@reatom/core`. */
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

/** A project fixture: a `ts.Program` plus an absolute-path resolver. */
export interface ProgramFixture {
  program: ts.Program;
  /** Absolute path of a fixture file by its relative name. */
  file: (relative: string) => string;
}

/** Builds a `ts.Program` from a set of fixtures `{ 'model.ts': '…' }`. */
export function createProgram(files: Record<string, string>): ProgramFixture {
  const dir = materialize(files);
  const program = ts.createProgram({
    rootNames: projectScriptNames(dir, files),
    options: COMPILER_OPTIONS,
  });
  return { program, file: (relative) => path.join(dir, relative) };
}

/** Removes the temporary fixture directories. Call in `afterAll`. */
export function cleanupFixtures(): void {
  for (const dir of createdDirs.splice(0)) {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}
