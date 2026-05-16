/**
 * Runtime-настройки фичи 2 (inlay hints). Доходят до плагина из IDE через
 * `_typescript.configurePlugin` → `onConfigurationChanged`, а при старте —
 * из секции `plugins` в `tsconfig.json` (`info.config`).
 */
export interface ReatomInlayHintsConfig {
  /** Глобальный тогл всей фичи. */
  enabled: boolean;
  /** Показывать роль сущности — `atom` / `computed` / `action` / `effect`. */
  showRole: boolean;
  /** Показывать сводку связей — `↑N` читатели, `↓N` писатели. */
  showCounts: boolean;
  /** Показывать список применённых `with*`-расширений. */
  showExtensions: boolean;
}

export const defaultConfig: ReatomInlayHintsConfig = {
  enabled: true,
  showRole: true,
  showCounts: true,
  showExtensions: true,
};

/**
 * Сливает сырой конфиг (из `tsconfig` или `configurePlugin`) с дефолтами.
 * Посторонние поля и значения неверного типа игнорируются — конфигом
 * управляет внешний код, доверять его форме нельзя.
 */
export function normalizeConfig(raw: unknown): ReatomInlayHintsConfig {
  const result: ReatomInlayHintsConfig = { ...defaultConfig };
  if (raw && typeof raw === 'object') {
    const source = raw as Record<string, unknown>;
    for (const key of Object.keys(defaultConfig) as Array<keyof ReatomInlayHintsConfig>) {
      const value = source[key];
      if (typeof value === 'boolean') result[key] = value;
    }
  }
  return result;
}
