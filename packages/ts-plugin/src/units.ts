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

import type * as ts from 'typescript';
import { isReatomSymbol } from './activation';

/** Role of a Reatom entity: `atom` / `computed` / `action` / `effect`. */
export type ReatomUnitKind = 'atom' | 'computed' | 'action' | 'effect';

/** Names of `@reatom/core` factories that create units → role. */
const FACTORY_KIND: Readonly<Record<string, ReatomUnitKind>> = {
  atom: 'atom',
  computed: 'computed',
  action: 'action',
  effect: 'effect',
};

/** A `with*` extension applied to a unit. */
export interface ReatomExtension {
  /** Extension name as written in code: `withAsync`, `withCache`, … */
  name: string;
}

/** A Reatom unit declaration found in the source. */
export interface ReatomUnit {
  kind: ReatomUnitKind;
  name: string;
  /** Declaration node — a stable key for usage counters. */
  declaration: ts.VariableDeclaration;
  /** `with*` extensions from `.extend(...)`, in application order. */
  extensions: ReatomExtension[];
}

/**
 * Strips the `.extend(...)` chain off the initializer and returns the base
 * factory call plus the collected `.extend` calls (outermost first in the
 * array).
 */
function unwrapExtendChain(
  tsm: typeof ts,
  expr: ts.Expression,
): { base: ts.CallExpression | undefined; extendCalls: ts.CallExpression[] } {
  const extendCalls: ts.CallExpression[] = [];
  let current: ts.Expression = expr;
  while (
    tsm.isCallExpression(current) &&
    tsm.isPropertyAccessExpression(current.expression) &&
    current.expression.name.text === 'extend'
  ) {
    extendCalls.push(current);
    current = current.expression.expression;
  }
  return { base: tsm.isCallExpression(current) ? current : undefined, extendCalls };
}

/** Callee identifier of a call: `atom(...)` → `atom`, `r.atom(...)` → `atom`. */
function calleeIdentifier(tsm: typeof ts, call: ts.CallExpression): ts.Identifier | undefined {
  const callee = call.expression;
  if (tsm.isIdentifier(callee)) return callee;
  if (tsm.isPropertyAccessExpression(callee) && tsm.isIdentifier(callee.name)) {
    return callee.name;
  }
  return undefined;
}

/** Unwraps an import alias down to the original symbol. */
function resolveAlias(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  symbol: ts.Symbol | undefined,
): ts.Symbol | undefined {
  if (symbol && symbol.flags & tsm.SymbolFlags.Alias) {
    try {
      return checker.getAliasedSymbol(symbol);
    } catch {
      /* getAliasedSymbol may throw on an incomplete alias — keep it as is */
    }
  }
  return symbol;
}

/**
 * Determines the role from the base factory call; `undefined` if it is not a
 * Reatom factory. Symbol resolution filters out foreign same-named functions.
 */
function classifyFactory(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  call: ts.CallExpression,
): ReatomUnitKind | undefined {
  const id = calleeIdentifier(tsm, call);
  if (!id) return undefined;

  const symbol = resolveAlias(tsm, checker, checker.getSymbolAtLocation(id));
  if (!isReatomSymbol(symbol)) return undefined;

  return FACTORY_KIND[symbol!.getName()];
}

/** Collects `with*` extension names from `.extend(...)` arguments. */
function collectExtensions(
  tsm: typeof ts,
  extendCalls: ts.CallExpression[],
): ReatomExtension[] {
  const extensions: ReatomExtension[] = [];
  // extendCalls runs "outer → inner", extensions apply in reverse order.
  for (const call of [...extendCalls].reverse()) {
    for (const arg of call.arguments) {
      const id = tsm.isCallExpression(arg)
        ? tsm.isIdentifier(arg.expression)
          ? arg.expression
          : undefined
        : tsm.isIdentifier(arg)
          ? arg
          : undefined;
      if (!id) continue;
      extensions.push({ name: id.text });
    }
  }
  return extensions;
}

function tryReadUnit(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  decl: ts.VariableDeclaration,
): ReatomUnit | undefined {
  if (!decl.initializer || !tsm.isIdentifier(decl.name)) return undefined;

  const { base, extendCalls } = unwrapExtendChain(tsm, decl.initializer);
  if (!base) return undefined;

  const kind = classifyFactory(tsm, checker, base);
  if (!kind) return undefined;

  return {
    kind,
    name: decl.name.text,
    declaration: decl,
    extensions: collectExtensions(tsm, extendCalls),
  };
}

/**
 * Finds all Reatom unit declarations in a file: `const X = atom(...)` and the
 * like, including `.extend(...)` chains.
 */
export function findReatomUnits(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  sourceFile: ts.SourceFile,
): ReatomUnit[] {
  const units: ReatomUnit[] = [];
  const visit = (node: ts.Node): void => {
    if (tsm.isVariableDeclaration(node)) {
      const unit = tryReadUnit(tsm, checker, node);
      if (unit) units.push(unit);
    }
    tsm.forEachChild(node, visit);
  };
  tsm.forEachChild(sourceFile, visit);
  return units;
}
