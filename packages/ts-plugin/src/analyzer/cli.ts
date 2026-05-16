import * as ts from 'typescript';
import * as path from 'node:path';
import { buildReatomGraph } from './graph';

/**
 * One-shot CLI анализатора реактивного графа (фича 6).
 *
 *   node dist/analyzer/cli.js --project path/to/tsconfig.json
 *
 * Создаёт `Program` из `tsconfig.json`, строит модель графа и печатает её
 * JSON в stdout. Этот канал использует IDE-плагин (фичи 8/9, вариант 2a):
 * данные графа, которые нельзя протащить через LSP, берутся прямым запуском
 * анализатора отдельным Node-процессом.
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
  if (!parsed) fail(`не удалось прочитать ${project}`);

  const program = ts.createProgram({
    rootNames: parsed.fileNames,
    options: parsed.options,
  });
  process.stdout.write(JSON.stringify(buildReatomGraph(ts, program)));
}

main();
