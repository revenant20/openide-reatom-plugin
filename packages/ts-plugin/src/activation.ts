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

/** Фрагмент пути, по которому опознаётся файл из пакета `@reatom/*`. */
const REATOM_PATH_FRAGMENT = '/@reatom/';

/**
 * Лежит ли символ в пакете `@reatom/*` — проверка по пути файла объявления.
 * Отсекает чужие одноимённые `atom` / `action` и т.п.: важно резолвить именно
 * символ, а не имя.
 */
export function isReatomSymbol(symbol: ts.Symbol | undefined): boolean {
  const declarations = symbol?.declarations;
  if (!declarations) return false;
  return declarations.some((decl) =>
    decl.getSourceFile().fileName.includes(REATOM_PATH_FRAGMENT),
  );
}
