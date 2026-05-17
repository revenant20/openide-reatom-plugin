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
