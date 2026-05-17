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

/** Path fragment that identifies a file belonging to a `@reatom/*` package. */
const REATOM_PATH_FRAGMENT = '/@reatom/';

/**
 * Whether the symbol lives in a `@reatom/*` package — checked by the path of
 * the declaration file. This filters out foreign same-named `atom` / `action`
 * and the like: it is essential to resolve the symbol, not the name.
 */
export function isReatomSymbol(symbol: ts.Symbol | undefined): boolean {
  const declarations = symbol?.declarations;
  if (!declarations) return false;
  return declarations.some((decl) =>
    decl.getSourceFile().fileName.includes(REATOM_PATH_FRAGMENT),
  );
}
