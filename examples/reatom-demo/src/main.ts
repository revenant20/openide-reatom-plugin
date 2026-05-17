import { addToCart, cartTotal, clearCart } from './cart';
import { discount } from './discount';
import { products } from './products';

/** Fills the cart, prints the totals, then clears it. */
function runDemo(): void {
  for (const product of products()) {
    addToCart(product.id);
  }
  console.log('Total:', cartTotal());
  console.log('Discount:', discount());
  clearCart();
}

runDemo();
