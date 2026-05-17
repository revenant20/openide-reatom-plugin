import { computed, withMemo } from '@reatom/core';
import { cartTotal } from './cart';

/**
 * A 10% discount once the cart total passes 300. The `withMemo` extension
 * is picked up by the analyzer and shown in the Code Lens summary above the
 * declaration.
 */
export const discount = computed(
  () => (cartTotal() > 300 ? cartTotal() * 0.1 : 0),
  'discount',
).extend(withMemo());
