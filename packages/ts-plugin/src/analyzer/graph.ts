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
import { findReatomUnits, ReatomUnitKind } from '../units';

/** Schema version of the graph model — a contract for consumers (CLI, IDE plugin). */
export const GRAPH_SCHEMA_VERSION = 1;

/** Range in a file — offsets; the consumer converts them to lines/columns itself. */
export interface GraphRange {
  start: number;
  end: number;
}

/** Graph node — a Reatom unit declaration. */
export interface ReatomGraphNode {
  /** Stable id: `${file}:${name}`. */
  id: string;
  kind: ReatomUnitKind;
  name: string;
  /** Absolute path of the declaration file. */
  file: string;
  /** Range of the declaration identifier. */
  range: GraphRange;
  /** Applied `with*` extensions. */
  extensions: string[];
}

/** Kind of a reactive relation. */
export type ReatomEdgeKind = 'read' | 'write' | 'extend';

/** Graph edge — a single usage of a unit. */
export interface ReatomGraphEdge {
  /** id of the unit being used. */
  to: string;
  /** id of the enclosing unit, if the usage is lexically inside one. */
  from?: string;
  kind: ReatomEdgeKind;
  /** Absolute path of the file where the usage occurs. */
  file: string;
  /** Range of the usage itself. */
  range: GraphRange;
}

/** Reactive graph model — versioned JSON, the analyzer output. */
export interface ReatomGraph {
  schemaVersion: number;
  nodes: ReatomGraphNode[];
  edges: ReatomGraphEdge[];
}

/** A project file — not `node_modules` and not `.d.ts`. */
function isProjectFile(sourceFile: ts.SourceFile): boolean {
  return (
    !sourceFile.isDeclarationFile && !sourceFile.fileName.includes('/node_modules/')
  );
}

/** Enclosing unit of an identifier: the nearest ancestor declaration from the set. */
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

/** Classifies an identifier usage as a graph edge. */
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
      /* incomplete alias */
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
 * Builds the reactive graph model from a ready `Program`. A pure function —
 * the foundation of layer 2 (CLI visualization, toolwindow, native Code Lens).
 */
export function buildReatomGraph(tsm: typeof ts, program: ts.Program): ReatomGraph {
  const checker = program.getTypeChecker();
  const projectFiles = program.getSourceFiles().filter(isProjectFile);

  // Step 1 — nodes: unit declarations across all project files.
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

  // Step 2 — edges: a single pass over identifiers in all project files.
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
