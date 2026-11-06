package com.supermarket;

/**
 * Compact Data Models for the Supermarket Billing System.
 */
public class Models {

    public record Product(int id, String barcode, String name, String category,
                          double price, int stock, double gst) {}

    public record Bill(int id, String billNo, String customer, String phone,
                       double subtotal, double discount, double tax, double grandTotal,
                       String paymentMode, String date) {}

    public static class CartItem {
        public final Product product;
        public int qty;

        public CartItem(Product product, int qty) {
            this.product = product;
            this.qty = qty;
        }

        public double gross() { return product.price() * qty; }
        public double tax() { return gross() * (product.gst() / 100.0); }
        public double total() { return gross() + tax(); }
    }
}
