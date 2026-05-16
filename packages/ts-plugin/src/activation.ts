import type * as ts from 'typescript';

/** Скоуп пакетов Reatom — всё под `@reatom/`. */
const REATOM_MODULE_RE = /^@reatom\//;

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

/**
 * «Reatom-файл» — импортирует или реэкспортирует что-либо из `@reatom/*`.
 * Это ленивая активация: если файл не трогает Reatom, плагин для него молчит
 * и не нагружает tsserver.
 */
export function isReatomFile(tsm: typeof ts, sourceFile: ts.SourceFile): boolean {
  for (const statement of sourceFile.statements) {
    if (
      (tsm.isImportDeclaration(statement) || tsm.isExportDeclaration(statement)) &&
      statement.moduleSpecifier !== undefined &&
      tsm.isStringLiteral(statement.moduleSpecifier) &&
      REATOM_MODULE_RE.test(statement.moduleSpecifier.text)
    ) {
      return true;
    }
  }
  return false;
}
