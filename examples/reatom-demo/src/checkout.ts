import { effect } from '@reatom/core';
import { cartCount } from './cart';
import { discount } from './discount';

/**
 * Logs the cart whenever its size or the discount changes. An effect reads
 * other units — click the gutter icon on `cartCount` or `discount` to jump
 * to their declarations.
 */
export const cartLogger = effect(() => {
  console.log(`Cart: ${cartCount()} item(s), discount ${discount()}`);
}, 'cartLogger');
