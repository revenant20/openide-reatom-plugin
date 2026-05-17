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

import { execSync } from 'node:child_process';
import * as path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Global vitest setup: builds the plugin once before the whole run.
 * Integration tests (tsserver, analyzer CLI) load code from `dist/` —
 * the build must be fresh and unique (no race of parallel `tsc`).
 */
export default function setup(): void {
  const packageRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  execSync('npm run build', { cwd: packageRoot, stdio: 'inherit' });
}
