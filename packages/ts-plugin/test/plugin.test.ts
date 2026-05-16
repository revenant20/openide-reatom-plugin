import { describe, it, expect, afterAll } from 'vitest';
import * as ts from 'typescript';
import { reatomTsPlugin } from '../src/plugin';
import { createLanguageService, cleanupFixtures } from './helpers';

afterAll(cleanupFixtures);

/** Минимальный `PluginCreateInfo` — плагину нужны только эти поля. */
function pluginInfo(
  service: ts.LanguageService,
  config: unknown,
): ts.server.PluginCreateInfo {
  return {
    languageService: service,
    languageServiceHost: {},
    serverHost: {},
    project: { projectService: { logger: { info() {} } } },
    config,
  } as unknown as ts.server.PluginCreateInfo;
}

/** Полный диапазон файла из LanguageService. */
function spanOf(service: ts.LanguageService, fileName: string): ts.TextSpan {
  const length = service.getProgram()?.getSourceFile(fileName)?.text.length ?? 0;
  return { start: 0, length };
}

describe('reatomTsPlugin', () => {
  it('create возвращает прокси, добавляющий Reatom inlay hints', () => {
    const { service, file } = createLanguageService({
      'model.ts':
        `import { atom } from '@reatom/core';\n` +
        `export const counter = atom(0, 'counter');`,
    });
    const proxy = reatomTsPlugin({ typescript: ts }).create(pluginInfo(service, {}));
    const fileName = file('model.ts');
    const hints = proxy.provideInlayHints(fileName, spanOf(service, fileName), undefined);
    const ours = hints.filter((h) =>
      (h.displayParts ?? []).some((p) => p.text.includes(': atom')),
    );
    expect(ours).toHaveLength(1);
  });

  it('enabled:false в конфиге отключает наши подсказки', () => {
    const { service, file } = createLanguageService({
      'model.ts':
        `import { atom } from '@reatom/core';\n` +
        `export const counter = atom(0, 'counter');`,
    });
    const proxy = reatomTsPlugin({ typescript: ts }).create(
      pluginInfo(service, { enabled: false }),
    );
    const fileName = file('model.ts');
    const hints = proxy.provideInlayHints(fileName, spanOf(service, fileName), undefined);
    const ours = hints.filter((h) =>
      (h.displayParts ?? []).some((p) => p.text.includes(': atom')),
    );
    expect(ours).toEqual([]);
  });

  it('не ломает остальные методы LanguageService', () => {
    const { service, file } = createLanguageService({
      'model.ts': `import { atom } from '@reatom/core';\nexport const counter = atom(0);`,
    });
    const proxy = reatomTsPlugin({ typescript: ts }).create(pluginInfo(service, {}));
    // getSemanticDiagnostics должен и дальше работать через прокси.
    expect(() => proxy.getSemanticDiagnostics(file('model.ts'))).not.toThrow();
  });
});
