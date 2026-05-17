import { atom } from '@reatom/core';

/** A single product in the catalogue. */
export interface Product {
  id: string;
  title: string;
  price: number;
}

/**
 * The product catalogue. A read-only source atom: other units read it and
 * nobody writes to it, so the gutter shows only the reader icon.
 */
export const products = atom<Product[]>(
  [
    { id: 'keyboard', title: 'Keyboard', price: 80 },
    { id: 'mouse', title: 'Mouse', price: 40 },
    { id: 'monitor', title: 'Monitor', price: 320 },
  ],
  'products',
);
