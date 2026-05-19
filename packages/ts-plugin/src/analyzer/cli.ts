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

import * as ts from 'typescript';
import * as fs from 'node:fs';
import * as path from 'node:path';
import {
  buildReatomGraph,
  GRAPH_SCHEMA_VERSION,
  type ReatomGraph,
  type ReatomGraphEdge,
  type ReatomGraphNode,
} from './graph';

/**
 * One-shot CLI of the reactive graph analyzer (feature 6).
 *
 *   node dist/analyzer/cli.js --project path/to/tsconfig.json
 *
 * Creates a `Program` from `tsconfig.json`, builds the graph model and prints
 * its JSON to stdout. The IDE plugin uses this channel (features 8/9, option
 * 2a): graph data that cannot be passed through LSP is obtained by running the
 * analyzer directly as a separate Node process.
 *
 * A solution-style `tsconfig.json` (`"files": []` plus `"references"`, the
 * default in Vite and monorepo setups) carries no files of its own — the
 * referenced leaf projects do. Such a config is expanded: every referenced
 * project is parsed recursively, analyzed in its own `Program`, and the
 * resulting graphs are merged. A referenced project that is missing or
 * unreadable is skipped (only an unreadable root config aborts the run).
 */

interface CliOptions {
  project: string;
}

/** A configuration file that could not be read or parsed. */
class ConfigError extends Error {}

function parseArgs(argv: readonly string[]): CliOptions {
  let project = 'tsconfig.json';
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if ((arg === '--project' || arg === '-p') && argv[i + 1]) {
      project = argv[i + 1];
      i += 1;
    } else if (arg.startsWith('--project=')) {
      project = arg.slice('--project='.length);
    }
  }
  return { project: path.resolve(project) };
}

function fail(message: string): never {
  process.stderr.write(`reatom-graph: ${message}\n`);
  process.exit(1);
}

/** Reports a non-fatal, skippable problem on stderr without aborting. */
function warn(message: string): void {
  process.stderr.write(`reatom-graph: ${message}\n`);
}

function describeError(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * Canonical key for the visited-set: resolves symlinks so the same config
 * reached through different paths is analyzed once. Falls back to a plain
 * normalization when the path cannot be resolved.
 */
function canonicalPath(configPath: string): string {
  try {
    return fs.realpathSync(configPath);
  } catch {
    return path.normalize(configPath);
  }
}

/**
 * Collects every parsed tsconfig that actually has files to compile, descending
 * recursively through `references`. A solution-style root config contributes no
 * files of its own; its referenced leaf projects are what gets analyzed. `seen`
 * guards against reference cycles and diamond reference graphs.
 *
 * A referenced project that is missing or unreadable is skipped with a warning;
 * an unreadable config throws — fatal for the root, caught-and-skipped for a
 * reference.
 */
function collectProjects(
  configPath: string,
  host: ts.ParseConfigFileHost,
  seen: Set<string>,
  out: ts.ParsedCommandLine[],
): void {
  const key = canonicalPath(configPath);
  if (seen.has(key)) return;
  seen.add(key);

  const parsed = ts.getParsedCommandLineOfConfigFile(configPath, undefined, host);
  if (!parsed) throw new ConfigError(`failed to read ${configPath}`);
  if (parsed.fileNames.length > 0) out.push(parsed);
  for (const reference of parsed.projectReferences ?? []) {
    const referencePath = ts.resolveProjectReferencePath(reference);
    if (!host.fileExists(referencePath)) {
      warn(`skipping missing referenced project ${referencePath}`);
      continue;
    }
    try {
      collectProjects(referencePath, host, seen, out);
    } catch (error) {
      warn(
        `skipping unreadable referenced project ${referencePath}: ${describeError(error)}`,
      );
    }
  }
}

/**
 * Merges per-project graphs into one. Nodes are deduplicated by their stable
 * id and edges by position, so a file pulled in by two referenced projects
 * never yields duplicate entries.
 */
function mergeGraphs(graphs: readonly ReatomGraph[]): ReatomGraph {
  const nodes = new Map<string, ReatomGraphNode>();
  const edges = new Map<string, ReatomGraphEdge>();
  for (const graph of graphs) {
    for (const node of graph.nodes) nodes.set(node.id, node);
    for (const edge of graph.edges) {
      const key = [
        edge.to,
        edge.from ?? '',
        edge.kind,
        edge.file,
        edge.range.start,
        edge.range.end,
      ].join('\u0000');
      edges.set(key, edge);
    }
  }
  return {
    schemaVersion: GRAPH_SCHEMA_VERSION,
    nodes: [...nodes.values()],
    edges: [...edges.values()],
  };
}

function main(): void {
  const { project } = parseArgs(process.argv.slice(2));

  const host: ts.ParseConfigFileHost = {
    useCaseSensitiveFileNames: ts.sys.useCaseSensitiveFileNames,
    readDirectory: ts.sys.readDirectory,
    fileExists: ts.sys.fileExists,
    readFile: ts.sys.readFile,
    getCurrentDirectory: ts.sys.getCurrentDirectory,
    onUnRecoverableConfigFileDiagnostic: (diagnostic) => {
      throw new ConfigError(ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
    },
  };

  const projects: ts.ParsedCommandLine[] = [];
  try {
    collectProjects(project, host, new Set<string>(), projects);
  } catch (error) {
    fail(describeError(error));
  }

  // `projectReferences` is passed so that, in a monorepo, an import of a
  // referenced project resolves to its source (the project-reference redirect)
  // rather than to a stale built `.d.ts` — keeping cross-project edges intact.
  const graphs = projects.map((parsed) =>
    buildReatomGraph(
      ts,
      ts.createProgram({
        rootNames: parsed.fileNames,
        options: parsed.options,
        projectReferences: parsed.projectReferences,
      }),
    ),
  );
  process.stdout.write(JSON.stringify(mergeGraphs(graphs)));
}

main();
