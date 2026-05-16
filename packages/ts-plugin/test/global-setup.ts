import { execSync } from 'node:child_process';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Глобальный setup vitest: один раз собирает плагин перед всем прогоном.
 * Интеграционные тесты (tsserver, CLI анализатора) грузят код из `dist/` —
 * сборка должна быть свежей и единственной (без гонки параллельных `tsc`).
 */
export default function setup(): void {
  const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  execSync('npm run build', { cwd: packageRoot, stdio: 'inherit' });
}
