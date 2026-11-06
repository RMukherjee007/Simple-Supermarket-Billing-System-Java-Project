package com.supermarket;

import com.supermarket.Models.Bill;
import com.supermarket.Models.CartItem;
import com.supermarket.Models.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-Performance Data Access Object (DAO) reusing a persistent JDBC connection:
 * - PreparedStatement CRUD
 * - Atomic JDBC Transactions (setAutoCommit(false), executeBatch(), commit(), rollback())
 * - SQL JOINs & Aggregations (SUM, COUNT, AVG, GROUP BY)
 */
public class SupermarketDAO {

    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY category, name";
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Product(
                        rs.getInt("id"), rs.getString("barcode"), rs.getString("name"),
                        rs.getString("category"), rs.getDouble("unit_price"),
                        rs.getInt("stock"), rs.getDouble("gst_rate")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void saveProduct(Product p) throws SQLException {
        Connection c = DBConnection.getConnection();
        // Check for duplicate barcode on another product
        try (PreparedStatement chk = c.prepareStatement("SELECT id FROM products WHERE barcode = ? AND id <> ?")) {
            chk.setString(1, p.barcode());
            chk.setInt(2, p.id());
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException("Barcode '" + p.barcode() + "' already exists. Select that item and click 'Update Selected' instead.");
                }
            }
        }

        String sql = p.id() == 0
                ? "INSERT INTO products (barcode, name, category, unit_price, stock, gst_rate) VALUES (?, ?, ?, ?, ?, ?)"
                : "UPDATE products SET barcode=?, name=?, category=?, unit_price=?, stock=?, gst_rate=? WHERE id=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.barcode());
            ps.setString(2, p.name());
            ps.setString(3, p.category());
            ps.setDouble(4, p.price());
            ps.setInt(5, p.stock());
            ps.setDouble(6, p.gst());
            if (p.id() != 0) ps.setInt(7, p.id());
            ps.executeUpdate();
        }
    }

    public void deleteProduct(int id) throws SQLException {
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Atomic JDBC Transaction: Inserts Bill, batch-inserts Bill Items, and decrements Product Stock.
     */
    public String saveBillTransaction(List<CartItem> items, String customer, String phone,
                                      double subtotal, double discount, double tax,
                                      double grandTotal, String paymentMode) throws SQLException {
        String billNo = "INV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd-HHmmss"));
        Connection conn = DBConnection.getConnection();
        conn.setAutoCommit(false);
        try {
            int billId;
            String billSql = "INSERT INTO bills (bill_no, customer_name, customer_phone, subtotal, discount, tax, grand_total, payment_mode) VALUES (?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = conn.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, billNo);
                ps.setString(2, customer.isBlank() ? "Walk-in Customer" : customer.trim());
                ps.setString(3, phone.isBlank() ? "-" : phone.trim());
                ps.setDouble(4, subtotal);
                ps.setDouble(5, discount);
                ps.setDouble(6, tax);
                ps.setDouble(7, grandTotal);
                ps.setString(8, paymentMode);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    billId = keys.getInt(1);
                }
            }

            String itemSql = "INSERT INTO bill_items (bill_id, product_id, product_name, qty, unit_price, gst_rate, line_total) VALUES (?,?,?,?,?,?,?)";
            String stockSql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";

            try (PreparedStatement itemPs = conn.prepareStatement(itemSql);
                 PreparedStatement stockPs = conn.prepareStatement(stockSql)) {
                for (CartItem ci : items) {
                    itemPs.setInt(1, billId);
                    itemPs.setInt(2, ci.product.id());
                    itemPs.setString(3, ci.product.name());
                    itemPs.setInt(4, ci.qty);
                    itemPs.setDouble(5, ci.product.price());
                    itemPs.setDouble(6, ci.product.gst());
                    itemPs.setDouble(7, ci.total());
                    itemPs.addBatch();

                    stockPs.setInt(1, ci.qty);
                    stockPs.setInt(2, ci.product.id());
                    stockPs.setInt(3, ci.qty);
                    stockPs.addBatch();
                }
                itemPs.executeBatch();
                for (int updated : stockPs.executeBatch()) {
                    if (updated == 0) throw new SQLException("Insufficient stock for one or more items.");
                }
            }

            conn.commit();
            return billNo;
        } catch (SQLException ex) {
            conn.rollback();
            throw ex;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public List<Bill> getBills() {
        List<Bill> list = new ArrayList<>();
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bills ORDER BY id DESC")) {
            while (rs.next()) {
                list.add(new Bill(
                        rs.getInt("id"), rs.getString("bill_no"), rs.getString("customer_name"),
                        rs.getString("customer_phone"), rs.getDouble("subtotal"), rs.getDouble("discount"),
                        rs.getDouble("tax"), rs.getDouble("grand_total"), rs.getString("payment_mode"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<CartItem> getBillItems(int billId) {
        List<CartItem> list = new ArrayList<>();
        try (PreparedStatement ps = DBConnection.getConnection().prepareStatement("SELECT * FROM bill_items WHERE bill_id = ?")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product p = new Product(rs.getInt("product_id"), "-", rs.getString("product_name"),
                            "", rs.getDouble("unit_price"), 0, rs.getDouble("gst_rate"));
                    list.add(new CartItem(p, rs.getInt("qty")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Map<String, Double> getCategorySales() {
        Map<String, Double> map = new LinkedHashMap<>();
        String sql = """
            SELECT COALESCE(p.category, 'Other') AS cat, SUM(bi.line_total) AS rev
            FROM bill_items bi LEFT JOIN products p ON bi.product_id = p.id
            GROUP BY COALESCE(p.category, 'Other')
        """;
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) map.put(rs.getString("cat"), rs.getDouble("rev"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

    public double[] getSummaryStats() {
        String sql = "SELECT COUNT(*), COALESCE(SUM(grand_total),0), COALESCE(AVG(grand_total),0) FROM bills";
        try (Statement st = DBConnection.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return new double[]{rs.getInt(1), rs.getDouble(2), rs.getDouble(3)};
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new double[]{0, 0, 0};
    }
}
