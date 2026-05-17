import type * as ts from 'typescript';
import { isReatomSymbol } from './activation';

/** Роль Reatom-сущности: `atom` / `computed` / `action` / `effect`. */
export type ReatomUnitKind = 'atom' | 'computed' | 'action' | 'effect';

/** Имена фабрик `@reatom/core`, создающих юниты, → роль. */
const FACTORY_KIND: Readonly<Record<string, ReatomUnitKind>> = {
  atom: 'atom',
  computed: 'computed',
  action: 'action',
  effect: 'effect',
};

/** Применённое к юниту `with*`-расширение. */
export interface ReatomExtension {
  /** Имя расширения, как в коде: `withAsync`, `withCache`, … */
  name: string;
}

/** Объявление Reatom-юнита, найденное в исходнике. */
export interface ReatomUnit {
  kind: ReatomUnitKind;
  name: string;
  /** Узел-объявление — стабильный ключ для счётчиков использований. */
  declaration: ts.VariableDeclaration;
  /** `with*`-расширения из `.extend(...)`, в порядке применения. */
  extensions: ReatomExtension[];
}

/**
 * Снимает цепочку `.extend(...)` с инициализатора и возвращает базовый вызов
 * фабрики плюс собранные `.extend`-вызовы (внешний — первым в массиве).
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

/** Идентификатор-callee вызова: `atom(...)` → `atom`, `r.atom(...)` → `atom`. */
function calleeIdentifier(tsm: typeof ts, call: ts.CallExpression): ts.Identifier | undefined {
  const callee = call.expression;
  if (tsm.isIdentifier(callee)) return callee;
  if (tsm.isPropertyAccessExpression(callee) && tsm.isIdentifier(callee.name)) {
    return callee.name;
  }
  return undefined;
}

/** Разворачивает alias импорта до исходного символа. */
function resolveAlias(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  symbol: ts.Symbol | undefined,
): ts.Symbol | undefined {
  if (symbol && symbol.flags & tsm.SymbolFlags.Alias) {
    try {
      return checker.getAliasedSymbol(symbol);
    } catch {
      /* getAliasedSymbol может бросить на незавершённом alias — оставляем как есть */
    }
  }
  return symbol;
}

/**
 * Определяет роль по базовому вызову фабрики; `undefined`, если это не фабрика
 * Reatom. Резолвинг символа отсекает чужие одноимённые функции.
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

/** Собирает имена `with*`-расширений из аргументов `.extend(...)`. */
function collectExtensions(
  tsm: typeof ts,
  extendCalls: ts.CallExpression[],
): ReatomExtension[] {
  const extensions: ReatomExtension[] = [];
  // extendCalls идёт «внешний → внутренний», применяются — в обратном порядке.
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
 * Находит все объявления Reatom-юнитов в файле: `const X = atom(...)` и т.п.,
 * включая цепочки `.extend(...)`.
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
