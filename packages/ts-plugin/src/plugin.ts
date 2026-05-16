import type * as ts from 'typescript';
import { normalizeConfig, ReatomInlayHintsConfig } from './config';
import { computeReatomInlayHints } from './inlay-hints';

const LOG_PREFIX = '[reatom-ts-plugin]';

/**
 * Фабрика TS Language Service Plugin. tsserver делает `require` модуля и зовёт
 * её, передавая свой экземпляр `typescript`.
 *
 * Что плагин делает: декорирует `provideInlayHints` у `LanguageService`,
 * дописывая Reatom-подсказки (фича 2). Остальные методы — без изменений.
 *
 * Подводный камень видимости: `typescript-language-server` не запросит
 * `provideInlayHints`, если у пользователя выключены ВСЕ штатные TS inlay
 * hints (гейт `areInlayHintsEnabledForFile`). Это вне зоны плагина — решается
 * на стороне frontend-плагина / настроек редактора (см. docs/feature-2-*).
 */
export function reatomTsPlugin(modules: {
  typescript: typeof ts;
}): ts.server.PluginModule {
  const tsm = modules.typescript;
  let config: ReatomInlayHintsConfig = normalizeConfig(undefined);

  function create(info: ts.server.PluginCreateInfo): ts.LanguageService {
    config = normalizeConfig(info.config);
    const languageService = info.languageService;

    // Прокси над LanguageService: все методы проксируются как есть, кроме
    // provideInlayHints. Стандартный паттерн TS-плагина.
    const proxy: ts.LanguageService = Object.create(null);
    for (const key of Object.keys(languageService) as Array<keyof ts.LanguageService>) {
      const original = languageService[key];
      (proxy as unknown as Record<string, unknown>)[key] =
        typeof original === 'function' ? original.bind(languageService) : original;
    }

    proxy.provideInlayHints = (fileName, span, preferences) => {
      const base = languageService.provideInlayHints(fileName, span, preferences);
      if (!config.enabled) return base;
      try {
        const program = languageService.getProgram();
        if (!program) return base;
        const reatomHints = computeReatomInlayHints(tsm, program, fileName, span, config);
        return reatomHints.length > 0 ? [...base, ...reatomHints] : base;
      } catch (error) {
        info.project.projectService.logger.info(
          `${LOG_PREFIX} provideInlayHints failed: ${String(error)}`,
        );
        return base;
      }
    };

    return proxy;
  }

  // Конфиг из IDE приходит сюда через `_typescript.configurePlugin`.
  function onConfigurationChanged(raw: unknown): void {
    config = normalizeConfig(raw);
  }

  return { create, onConfigurationChanged };
}
