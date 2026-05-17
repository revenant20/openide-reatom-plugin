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
import * as path from 'node:path';
import { buildReatomGraph } from './graph';

/**
 * One-shot CLI of the reactive graph analyzer (feature 6).
 *
 *   node dist/analyzer/cli.js --project path/to/tsconfig.json
 *
 * Creates a `Program` from `tsconfig.json`, builds the graph model and prints
 * its JSON to stdout. The IDE plugin uses this channel (features 8/9, option
 * 2a): graph data that cannot be passed through LSP is obtained by running the
 * analyzer directly as a separate Node process.
 */

interface CliOptions {
  project: string;
}

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

function main(): void {
  const { project } = parseArgs(process.argv.slice(2));

  const host: ts.ParseConfigFileHost = {
    useCaseSensitiveFileNames: ts.sys.useCaseSensitiveFileNames,
    readDirectory: ts.sys.readDirectory,
    fileExists: ts.sys.fileExists,
    readFile: ts.sys.readFile,
    getCurrentDirectory: ts.sys.getCurrentDirectory,
    onUnRecoverableConfigFileDiagnostic: (diagnostic) => {
      fail(ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'));
    },
  };

  const parsed = ts.getParsedCommandLineOfConfigFile(project, undefined, host);
  if (!parsed) fail(`failed to read ${project}`);

  const program = ts.createProgram({
    rootNames: parsed.fileNames,
    options: parsed.options,
  });
  process.stdout.write(JSON.stringify(buildReatomGraph(ts, program)));
}

main();
