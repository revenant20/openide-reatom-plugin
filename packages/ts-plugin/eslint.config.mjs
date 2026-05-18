// ESLint flat config for @openide/reatom-ts-plugin.
//
// Static analysis of the TypeScript sources of the reactive graph analyzer.
// `npm run lint` (and the Gradle `:ts-plugin:check` task) run it. The rule set
// is ESLint's recommended JS rules plus typescript-eslint's recommended rules.

import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist/', 'build/', 'node_modules/'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
);
