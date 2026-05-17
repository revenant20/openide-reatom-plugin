import { action, atom, computed } from '@reatom/core';
import { products } from './products';

/**
 * Product id -> quantity in the cart. It is both read (by the computed atoms
 * below) and written (by the actions), so the gutter shows both icons.
 */
export const cartItems = atom<Record<string, number>>({}, 'cartItems');

/** The total number of items in the cart. */
export const cartCount = computed(
  () => Object.values(cartItems()).reduce((sum, quantity) => sum + quantity, 0),
  'cartCount',
);

/** The cart total — reads `products` (from another file) and `cartItems`. */
export const cartTotal = computed(() => {
  const quantities = cartItems();
  return products().reduce(
    (sum, product) => sum + (quantities[product.id] ?? 0) * product.price,
    0,
  );
}, 'cartTotal');

/** Adds one unit of a product to the cart. */
export const addToCart = action((productId: string) => {
  cartItems.set((quantities) => ({
    ...quantities,
    [productId]: (quantities[productId] ?? 0) + 1,
  }));
}, 'addToCart');

/** Empties the cart. */
export const clearCart = action(() => {
  cartItems.set({});
}, 'clearCart');
