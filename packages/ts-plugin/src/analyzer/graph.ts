import type * as ts from 'typescript';
import { findReatomUnits, ReatomUnitKind } from '../units';

/** Версия схемы модели графа — контракт для потребителей (CLI, IDE-плагин). */
export const GRAPH_SCHEMA_VERSION = 1;

/** Диапазон в файле — offset'ы, потребитель сам конвертирует в строки/колонки. */
export interface GraphRange {
  start: number;
  end: number;
}

/** Узел графа — объявление Reatom-юнита. */
export interface ReatomGraphNode {
  /** Стабильный id: `${file}:${name}`. */
  id: string;
  kind: ReatomUnitKind;
  name: string;
  /** Абсолютный путь файла объявления. */
  file: string;
  /** Диапазон идентификатора объявления. */
  range: GraphRange;
  /** Применённые `with*`-расширения. */
  extensions: string[];
}

/** Тип реактивной связи. */
export type ReatomEdgeKind = 'read' | 'write' | 'extend';

/** Ребро графа — одно использование юнита. */
export interface ReatomGraphEdge {
  /** id юнита, который используют. */
  to: string;
  /** id объемлющего юнита, если использование лексически внутри него. */
  from?: string;
  kind: ReatomEdgeKind;
  /** Абсолютный путь файла, где использование. */
  file: string;
  /** Диапазон самого использования. */
  range: GraphRange;
}

/** Модель реактивного графа — versioned JSON, выход анализатора. */
export interface ReatomGraph {
  schemaVersion: number;
  nodes: ReatomGraphNode[];
  edges: ReatomGraphEdge[];
}

/** Файл проекта — не `node_modules` и не `.d.ts`. */
function isProjectFile(sourceFile: ts.SourceFile): boolean {
  return (
    !sourceFile.isDeclarationFile && !sourceFile.fileName.includes('/node_modules/')
  );
}

/** Объемлющий юнит идентификатора: ближайший предок-объявление из набора. */
function enclosingUnit(
  tsm: typeof ts,
  id: ts.Identifier,
  nodeByDeclaration: ReadonlyMap<ts.VariableDeclaration, ReatomGraphNode>,
): ReatomGraphNode | undefined {
  for (let node: ts.Node | undefined = id.parent; node; node = node.parent) {
    if (tsm.isVariableDeclaration(node)) {
      const found = nodeByDeclaration.get(node);
      if (found) return found;
    }
  }
  return undefined;
}

/** Классифицирует использование идентификатора как ребро графа. */
function classifyEdge(
  tsm: typeof ts,
  checker: ts.TypeChecker,
  id: ts.Identifier,
  nodeByDeclaration: ReadonlyMap<ts.VariableDeclaration, ReatomGraphNode>,
): ReatomGraphEdge | undefined {
  const parent = id.parent;
  if (tsm.isVariableDeclaration(parent) && parent.name === id) return undefined;

  let kind: ReatomEdgeKind | undefined;
  if (tsm.isCallExpression(parent) && parent.expression === id) {
    kind = 'read';
  } else if (
    tsm.isPropertyAccessExpression(parent) &&
    parent.expression === id &&
    tsm.isCallExpression(parent.parent) &&
    parent.parent.expression === parent
  ) {
    if (parent.name.text === 'set') kind = 'write';
    else if (parent.name.text === 'extend') kind = 'extend';
  }
  if (!kind) return undefined;

  let symbol = checker.getSymbolAtLocation(id);
  if (symbol && symbol.flags & tsm.SymbolFlags.Alias) {
    try {
      symbol = checker.getAliasedSymbol(symbol);
    } catch {
      /* незавершённый alias */
    }
  }

  let target: ReatomGraphNode | undefined;
  for (const declaration of symbol?.declarations ?? []) {
    if (tsm.isVariableDeclaration(declaration)) {
      const node = nodeByDeclaration.get(declaration);
      if (node) {
        target = node;
        break;
      }
    }
  }
  if (!target) return undefined;

  const sourceFile = id.getSourceFile();
  const edge: ReatomGraphEdge = {
    to: target.id,
    kind,
    file: sourceFile.fileName,
    range: { start: id.getStart(sourceFile), end: id.getEnd() },
  };
  const from = enclosingUnit(tsm, id, nodeByDeclaration);
  if (from) edge.from = from.id;
  return edge;
}

/**
 * Строит модель реактивного графа по готовой `Program`. Чистая функция —
 * фундамент слоя 2 (CLI-визуализация, toolwindow, нативный Code Lens).
 */
export function buildReatomGraph(tsm: typeof ts, program: ts.Program): ReatomGraph {
  const checker = program.getTypeChecker();
  const projectFiles = program.getSourceFiles().filter(isProjectFile);

  // Шаг 1 — узлы: объявления юнитов по всем файлам проекта.
  const nodes: ReatomGraphNode[] = [];
  const nodeByDeclaration = new Map<ts.VariableDeclaration, ReatomGraphNode>();
  for (const sourceFile of projectFiles) {
    for (const unit of findReatomUnits(tsm, checker, sourceFile)) {
      const name = unit.declaration.name;
      const node: ReatomGraphNode = {
        id: `${sourceFile.fileName}:${unit.name}`,
        kind: unit.kind,
        name: unit.name,
        file: sourceFile.fileName,
        range: { start: name.getStart(sourceFile), end: name.getEnd() },
        extensions: unit.extensions.map((extension) => extension.name),
      };
      nodes.push(node);
      nodeByDeclaration.set(unit.declaration, node);
    }
  }

  // Шаг 2 — рёбра: один проход по идентификаторам всех файлов проекта.
  const edges: ReatomGraphEdge[] = [];
  if (nodeByDeclaration.size > 0) {
    for (const sourceFile of projectFiles) {
      const visit = (node: ts.Node): void => {
        if (tsm.isIdentifier(node)) {
          const edge = classifyEdge(tsm, checker, node, nodeByDeclaration);
          if (edge) edges.push(edge);
        }
        tsm.forEachChild(node, visit);
      };
      tsm.forEachChild(sourceFile, visit);
    }
  }

  return { schemaVersion: GRAPH_SCHEMA_VERSION, nodes, edges };
}
