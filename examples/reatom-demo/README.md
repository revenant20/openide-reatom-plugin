# Reatom demo

A small consumer project for trying out **openide-reatom-plugin** — a toy
shopping cart built with `@reatom/core`:

- `src/products.ts` — the catalogue (a read-only source atom);
- `src/cart.ts` — cart state, derived totals (`computed`), and actions;
- `src/discount.ts` — a derived atom with the `withCache` extension;
- `src/checkout.ts` — an `effect` that reads other units;
- `src/main.ts` — usage sites.

## Trying it

```bash
npm install
```

Then open this directory in an IDE with the plugin installed. Above each
`atom` / `computed` / `action` / `effect` declaration a Code Lens summary
appears, and the gutter shows reader / writer icons on declarations and
jump-to-declaration icons on usage lines.

`npm run typecheck` runs `tsc` over the sources.
