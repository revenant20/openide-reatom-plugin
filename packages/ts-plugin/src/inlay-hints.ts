import type * as ts from 'typescript';
import { isReatomFile } from './activation';
import { findReatomUnits, ReatomUnit } from './units';
import { analyzeProgram, UnitRefCounts } from './references';
import { ReatomInlayHintsConfig } from './config';

/** Разделитель сегментов подписи. */
const SEPARATOR = ' · ';

/**
 * Считает Reatom inlay hints для файла в пределах `span`. Чистая функция над
 * готовой `Program` — её зовёт декоратор `provideInlayHints`, ту же функцию
 * напрямую гоняют тесты.
 */
export function computeReatomInlayHints(
  tsm: typeof ts,
  program: ts.Program,
  fileName: string,
  span: ts.TextSpan,
  config: ReatomInlayHintsConfig,
): ts.InlayHint[] {
  if (!config.enabled || !config.showRole) return [];

  const sourceFile = program.getSourceFile(fileName);
  if (!sourceFile || sourceFile.isDeclarationFile) return [];
  if (!isReatomFile(tsm, sourceFile)) return []; // ленивая активация

  const checker = program.getTypeChecker();
  const units = findReatomUnits(tsm, checker, sourceFile);
  if (units.length === 0) return [];

  const spanEnd = span.start + span.length;
  const visible = units.filter(
    (unit) => unit.namePosition >= span.start && unit.namePosition <= spanEnd,
  );
  if (visible.length === 0) return [];

  // Счётчики дороги — считаем только если они показываются; результат
  // кэшируется по `Program`, так что повторные вызовы дешёвые.
  const counts = config.showCounts ? analyzeProgram(tsm, program).counts : undefined;

  return visible.map((unit) =>
    buildHint(tsm, unit, config, counts?.get(unit.declaration)),
  );
}

function buildHint(
  tsm: typeof ts,
  unit: ReatomUnit,
  config: ReatomInlayHintsConfig,
  refCounts: UnitRefCounts | undefined,
): ts.InlayHint {
  const parts: ts.InlayHintDisplayPart[] = [{ text: ': ' + unit.kind }];

  if (config.showCounts && refCounts) {
    parts.push({ text: SEPARATOR + '↑' + refCounts.readers });
    parts.push({ text: SEPARATOR + '↓' + refCounts.writers });
  }

  if (config.showExtensions) {
    for (const ext of unit.extensions) {
      parts.push({ text: SEPARATOR });
      // Сегмент-расширение навигируем, если известно место определения:
      // `span` + `file` маппятся в `InlayHintLabelPart.location`.
      parts.push(
        ext.target
          ? {
              text: '⤴' + ext.name,
              span: {
                start: ext.target.start,
                length: ext.target.end - ext.target.start,
              },
              file: ext.target.fileName,
            }
          : { text: '⤴' + ext.name },
      );
    }
  }

  return {
    text: '',
    displayParts: parts,
    position: unit.namePosition,
    kind: tsm.InlayHintKind.Type,
    whitespaceBefore: true,
  };
}
