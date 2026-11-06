# Viva Voce (15M) & Descriptive Test (5M) Preparation Guide

This guide prepares you to answer every question during your **Viva (A3)** and write clean syntax during your **Descriptive Test (A1)**.

---

## 1. Why Does This Project Qualify for "Outstanding (15/15 Marks)"?
- **Basic (Max 12M)** uses AWT + SQLite + basic CRUD.
- **Advanced (Max 14M)** uses Swing + MySQL + basic CRUD.
- **Our Project (Outstanding - Max 15M)** uses **JavaFX**, **Multi-type Event Handling**, and **MySQL with Rich Database Operations** (JDBC Atomic Transactions, Batch Execution, Foreign Key Joins, Aggregate Queries, and JavaFX Data Visualization Charts).

---

## 2. Top Viva Questions & Answers (Concept + Exact Syntax)

### Q1: How does JavaFX architecture work in your project?
**Concept:**
Every JavaFX application extends `javafx.application.Application` and overrides the `start(Stage primaryStage)` lifecycle method.
- **`Stage`**: The top-level OS window (`primaryStage`).
- **`Scene`**: The container for all UI elements inside the Stage (`new Scene(root, 1340, 820)`).
- **Scene Graph (`Node`s)**: Hierarchical tree of layout panes (`BorderPane`, `TabPane`, `SplitPane`, `VBox`, `HBox`, `GridPane`) and controls (`TableView`, `PieChart`, `BarChart`, `Button`, `TextField`).

**Syntax to Write:**
```java
public class SupermarketApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root, 1340, 820);
        primaryStage.setTitle("Supermarket Billing System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    public static void main(String[] args) {
        launch(args);
    }
}
```

---

### Q2: What types of Event Handling have you implemented?
**Concept:**
We implemented **4 distinct types of Event Handling** in `SupermarketApp.java`:
1. **`ActionEvent`** (Button clicks, ComboBox dropdown changes, CheckBox toggles):
   ```java
   checkoutBtn.setOnAction(e -> handleCheckoutTransaction());
   ```
2. **`KeyEvent`** (Barcode scanner `ENTER` key press in `barcodeScanField`):
   ```java
   barcodeScanField.setOnKeyPressed(event -> {
       if (event.getCode() == KeyCode.ENTER) {
           handleBarcodeScan();
       }
   });
   ```
3. **`MouseEvent`** (Double-clicking a product row in the `TableView` to add it to the cart):
   ```java
   posCatalogTable.setRowFactory(tv -> {
       TableRow<Product> row = new TableRow<>();
       row.setOnMouseClicked(event -> {
           if (event.getClickCount() == 2 && !row.isEmpty()) {
               addSelectedProductToCart(row.getItem());
           }
       });
       return row;
   });
   ```
4. **`ChangeListener` & `ListChangeListener`** (Live search bar filtering & reactive cart totals):
   ```java
   posSearchField.textProperty().addListener((obs, oldVal, newVal) -> refreshPosCatalog());
   cartItemList.addListener((ListChangeListener<CartItem>) change -> updateCartTotalsAndReceipt(null));
   ```

---

### Q3: What "Rich Database Operations" are used in `SupermarketDAO.java`?
**Concept:**
Instead of isolated `INSERT` or `SELECT` statements, a real supermarket checkout modifies multiple tables at once (`customers`, `bills`, `bill_items`, and `products`). If stock runs out on item #3, the bill must not be saved partially. Therefore, we use:
1. **JDBC Atomic Transactions (`setAutoCommit(false)`, `commit()`, `rollback()`)**
2. **Batch Processing (`addBatch()`, `executeBatch()`)**
3. **Auto-Generated Primary Keys (`Statement.RETURN_GENERATED_KEYS`)**
4. **SQL Joins & Aggregations (`LEFT JOIN`, `SUM`, `COUNT`, `AVG`, `GROUP BY`)**

**Syntax to Write (Transactional Checkout):**
```java
try (Connection conn = DBConnection.getConnection()) {
    conn.setAutoCommit(false); // Start transaction
    try {
        // 1. Insert Bill and get generated bill_id
        PreparedStatement billPs = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS);
        billPs.executeUpdate();
        ResultSet keys = billPs.getGeneratedKeys();
        keys.next();
        int billId = keys.getInt(1);

        // 2. Batch insert bill_items & batch decrement product stock
        PreparedStatement itemPs = conn.prepareStatement(insertItemSql);
        PreparedStatement stockPs = conn.prepareStatement(decrementStockSql);
        for (CartItem item : cartItems) {
            itemPs.setInt(1, billId);
            itemPs.addBatch();

            stockPs.setInt(1, item.getQuantity());
            stockPs.setInt(2, item.getProductId());
            stockPs.addBatch();
        }
        itemPs.executeBatch();
        stockPs.executeBatch();

        conn.commit(); // Commit all changes atomically
    } catch (SQLException ex) {
        conn.rollback(); // Undo everything if any step fails
        throw ex;
    }
}
```

---

### Q4: Why do we use `PreparedStatement` instead of `Statement`?
**Concept:**
1. **Prevents SQL Injection**: User input parameters (`?`) are bound safely using `ps.setString(1, value)` and never concatenated into raw SQL.
2. **Performance**: MySQL pre-compiles the SQL execution plan and reuses it across batch executions (`addBatch()`).

---

### Q5: How does JavaFX `TableView` data binding work?
**Concept:**
Model classes (`Product`, `Customer`, `CartItem`, `BillRecord`) use **JavaFX Observable Properties** (`SimpleStringProperty`, `SimpleDoubleProperty`, `SimpleIntegerProperty`) instead of plain fields, and the `TableView` is backed by an `ObservableList<T>`. Whenever an item is added, edited, or removed from the `ObservableList`, the `TableView` updates the UI automatically.
```java
TableColumn<Product, String> colName = new TableColumn<>("Product Name");
colName.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
```
