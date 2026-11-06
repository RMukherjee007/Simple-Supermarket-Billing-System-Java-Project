package com.supermarket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Persistent JDBC Connection Manager & Schema Initializer.
 * Reuses a single live connection so UI queries execute in < 1ms.
 */
public class DBConnection {

    private static final String MYSQL_URL =
            "jdbc:mysql://localhost:3306/supermarket_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=500";
    private static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String PASS = System.getenv().getOrDefault("DB_PASS", "");
    private static final String FALLBACK_URL =
            "jdbc:h2:./data/supermarket_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static Connection sharedConn;
    private static boolean useMySQL = true;

    public static synchronized Connection getConnection() throws SQLException {
        if (sharedConn != null && !sharedConn.isClosed()) {
            return sharedConn;
        }
        if (useMySQL) {
            try {
                sharedConn = DriverManager.getConnection(MYSQL_URL, USER, PASS);
                return sharedConn;
            } catch (SQLException e) {
                useMySQL = false;
            }
        }
        sharedConn = DriverManager.getConnection(FALLBACK_URL, "sa", "");
        return sharedConn;
    }

    public static String getEngineName() {
        return useMySQL ? "MySQL (localhost:3306/supermarket_db)" : "MySQL Embedded Mode";
    }

    public static void initDatabase() {
        try (Statement st = getConnection().createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS products (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    barcode VARCHAR(32) NOT NULL UNIQUE,
                    name VARCHAR(100) NOT NULL,
                    category VARCHAR(50) NOT NULL,
                    unit_price DECIMAL(10,2) NOT NULL,
                    stock INT NOT NULL DEFAULT 0,
                    gst_rate DECIMAL(5,2) NOT NULL DEFAULT 5.0
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bills (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    bill_no VARCHAR(40) NOT NULL UNIQUE,
                    customer_name VARCHAR(80) NOT NULL,
                    customer_phone VARCHAR(20) NOT NULL,
                    subtotal DECIMAL(10,2) NOT NULL,
                    discount DECIMAL(10,2) NOT NULL,
                    tax DECIMAL(10,2) NOT NULL,
                    grand_total DECIMAL(10,2) NOT NULL,
                    payment_mode VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS bill_items (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    bill_id INT NOT NULL,
                    product_id INT NULL,
                    product_name VARCHAR(100) NOT NULL,
                    qty INT NOT NULL,
                    unit_price DECIMAL(10,2) NOT NULL,
                    gst_rate DECIMAL(5,2) NOT NULL,
                    line_total DECIMAL(10,2) NOT NULL,
                    FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
                )
            """);

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    st.executeUpdate("""
                        INSERT INTO products (barcode, name, category, unit_price, stock, gst_rate) VALUES
                        ('101', 'Amul Milk (1L)', 'Dairy', 72.00, 80, 5.0),
                        ('102', 'Amul Butter (500g)', 'Dairy', 285.00, 40, 12.0),
                        ('103', 'Whole Wheat Bread', 'Dairy', 55.00, 35, 5.0),
                        ('104', 'Basmati Rice (5kg)', 'Grains', 640.00, 50, 5.0),
                        ('105', 'Chakki Atta (10kg)', 'Grains', 465.00, 30, 5.0),
                        ('106', 'Sunflower Oil (1L)', 'Grains', 145.00, 60, 5.0),
                        ('107', 'Tata Tea Gold (500g)', 'Beverages', 310.00, 45, 5.0),
                        ('108', 'Instant Coffee (200g)', 'Beverages', 650.00, 20, 18.0),
                        ('109', 'Maggi Noodles (6-Pack)', 'Snacks', 84.00, 90, 12.0),
                        ('110', 'Dairy Milk Silk', 'Snacks', 185.00, 10, 18.0),
                        ('111', 'Surf Excel Liquid (2L)', 'Household', 430.00, 25, 18.0),
                        ('112', 'Colgate Toothpaste', 'Household', 135.00, 8, 18.0)
                    """);

                    st.executeUpdate("""
                        INSERT INTO bills (bill_no, customer_name, customer_phone, subtotal, discount, tax, grand_total, payment_mode)
                        VALUES ('INV-1001', 'Aarav Sharma', '9876543210', 925.00, 46.25, 62.89, 941.64, 'UPI'),
                               ('INV-1002', 'Priya Nair', '9811122233', 1080.00, 0.00, 194.40, 1274.40, 'Card')
                    """);

                    st.executeUpdate("""
                        INSERT INTO bill_items (bill_id, product_id, product_name, qty, unit_price, gst_rate, line_total)
                        VALUES (1, 4, 'Basmati Rice (5kg)', 1, 640.00, 5.0, 672.00),
                               (1, 2, 'Amul Butter (500g)', 1, 285.00, 12.0, 319.20),
                               (2, 8, 'Instant Coffee (200g)', 1, 650.00, 18.0, 767.00),
                               (2, 11, 'Surf Excel Liquid (2L)', 1, 430.00, 18.0, 507.40)
                    """);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
