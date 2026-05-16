import type * as ts from 'typescript';
import { findReatomUnits } from './units';

/** Счётчики использований одного юнита по всему проекту. */
export interface UnitRefCounts {
  /** Чтения — вызовы `unit()`. */
  readers: number;
  /** Записи — вызовы `unit.set(...)`. */
  writers: number;
}

/** Результат анализа проекта: счётчики, ключ — узел-объявление юнита. */
export interface ProgramAnalysis {
  counts: Map<ts.VariableDeclaration, UnitRefCounts>;
}

/**
 * Кэш анализа по объекту `Program`. tsserver выдаёт новый `Program` после
 * каждой правки — старый ключ отмирает вместе со значением (`WeakMap`),
 * новый считается заново. В пределах одной программы (скролл, повторные
 * вызовы `provideInlayHints`) — попадание в кэш.
 */
const analysisCache = new WeakMap<ts.Program, ProgramAnalysis>();

/** Файл проекта — не `node_modules` и не `.d.ts`; только такие обходим. */
function isProjectFile(sourceFile: ts.SourceFile): boolean {
  return (
    !sourceFile.isDeclarationFile && !sourceFile.fileName.includes('/node_modules/')
  );
}

/**
 * Резолвит идентификатор до узлов-объявлений, разворачивая alias импорта.
 * Сопоставление по узлу-объявлению (а не по символу) обходит расхождение
 * локального и export-символа при кросс-файловом использовании.
 */
function resolveDeclarations(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  id: ts.Identifier,
): readonly ts.Declaration[] {
  let symbol = checker.getSymbolAtLocation(id);
  if (symbol && symbol.flags & tsm.SymbolFlags.Alias) {
    try {
      symbol = checker.getAliasedSymbol(symbol);
    } catch {
      /* незавершённый alias — оставляем как есть */
    }
  }
  return symbol?.declarations ?? [];
}

function classifyUsage(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  id: ts.Identifier,
  counts: Map<ts.VariableDeclaration, UnitRefCounts>,
): void {
  const parent = id.parent;

  // Идентификатор в объявлении самого юнита — не использование.
  if (tsm.isVariableDeclaration(parent) && parent.name === id) return;

  // Интересны только два контекста; всё прочее (включая `.subscribe`,
  // `.extend`, передачу юнита по ссылке) не считаем — и не платим за резолвинг.
  const isReadCallee = tsm.isCallExpression(parent) && parent.expression === id;
  const isWriteTarget =
    tsm.isPropertyAccessExpression(parent) &&
    parent.expression === id &&
    parent.name.text === 'set' &&
    tsm.isCallExpression(parent.parent) &&
    parent.parent.expression === parent;
  if (!isReadCallee && !isWriteTarget) return;

  for (const decl of resolveDeclarations(tsm, checker, id)) {
    if (!tsm.isVariableDeclaration(decl)) continue;
    const bucket = counts.get(decl);
    if (!bucket) continue;
    if (isWriteTarget) bucket.writers++;
    else bucket.readers++;
    return;
  }
}

function countUsagesInFile(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  sourceFile: ts.SourceFile,
  counts: Map<ts.VariableDeclaration, UnitRefCounts>,
): void {
  const visit = (node: ts.Node): void => {
    if (tsm.isIdentifier(node)) classifyUsage(tsm, checker, node, counts);
    tsm.forEachChild(node, visit);
  };
  tsm.forEachChild(sourceFile, visit);
}

/**
 * Анализирует весь проект: считает чтения/записи каждого юнита одним проходом
 * по идентификаторам. Результат кэшируется по `Program`.
 */
export function analyzeProgram(tsm: typeof ts, program: ts.Program): ProgramAnalysis {
  const cached = analysisCache.get(program);
  if (cached) return cached;

  const checker = program.getTypeChecker();
  const projectFiles = program.getSourceFiles().filter(isProjectFile);

  // Шаг 1 — собрать узлы-объявления всех юнитов проекта.
  const counts = new Map<ts.VariableDeclaration, UnitRefCounts>();
  for (const sourceFile of projectFiles) {
    for (const unit of findReatomUnits(tsm, checker, sourceFile)) {
      if (!counts.has(unit.declaration)) {
        counts.set(unit.declaration, { readers: 0, writers: 0 });
      }
    }
  }

  // Шаг 2 — один проход по идентификаторам, классификация использований.
  if (counts.size > 0) {
    for (const sourceFile of projectFiles) {
      countUsagesInFile(tsm, checker, sourceFile, counts);
    }
  }

  const analysis: ProgramAnalysis = { counts };
  analysisCache.set(program, analysis);
  return analysis;
}
