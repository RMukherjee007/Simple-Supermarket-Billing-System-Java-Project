package com.supermarket;

import com.supermarket.Models.Bill;
import com.supermarket.Models.CartItem;
import com.supermarket.Models.Product;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Supermarket Billing System - Fast, Streamlined JavaFX GUI with Event Handling & MySQL DAO.
 */
public class SupermarketApp extends Application {

    private final SupermarketDAO dao = new SupermarketDAO();
    private final ObservableList<Product> catalogData = FXCollections.observableArrayList();
    private final ObservableList<CartItem> cartData = FXCollections.observableArrayList();
    private final ObservableList<Product> inventoryData = FXCollections.observableArrayList();
    private final ObservableList<Bill> billHistoryData = FXCollections.observableArrayList();

    private TextField posSearch, barcodeField, custName, custPhone, discField;
    private ComboBox<String> posCategory, payMode;
    private Spinner<Integer> qtySpinner;
    private TableView<CartItem> cartTable;
    private TableView<Bill> billTable;
    private Label totalLabel, statsBanner;
    private TextArea receiptArea, historyReceiptArea;
    private PieChart salesPieChart;

    private static final String[] CATEGORIES = {"All", "Dairy", "Grains", "Beverages", "Snacks", "Household"};

    @Override
    public void start(Stage stage) {
        DBConnection.initDatabase();

        HBox header = new HBox(15);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: #1e3a8a;");
        Label title = new Label("Supermarket Billing System");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: white;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label dbInfo = new Label("Database: " + DBConnection.getEngineName());
        dbInfo.setStyle("-fx-text-fill: #e2e8f0; -fx-font-weight: bold;");
        header.getChildren().addAll(title, spacer, dbInfo);

        TabPane tabs = new TabPane(
                new Tab("Billing Counter", buildPosTab()),
                new Tab("Inventory (CRUD)", buildInventoryTab()),
                new Tab("Sales Reports & History", buildReportsTab())
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        BorderPane root = new BorderPane(tabs);
        root.setTop(header);
        refreshAll();
        updateCartAndReceipt("DRAFT");

        stage.setTitle("Supermarket Billing System");
        stage.setScene(new Scene(root, 1220, 740));
        stage.show();
    }

    // --- TAB 1: POS BILLING COUNTER ---
    private SplitPane buildPosTab() {
        barcodeField = new TextField();
        barcodeField.setPromptText("Scan/Type Barcode (e.g. 101) + Press ENTER");
        barcodeField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                String code = barcodeField.getText().trim();
                Product p = inventoryData.stream().filter(x -> x.barcode().equalsIgnoreCase(code)).findFirst().orElse(null);
                if (p != null) { addToCart(p, getSpinnerQty()); barcodeField.clear(); }
                else alert("Not Found", "No product found with barcode: " + code);
            }
        });

        posSearch = new TextField();
        posSearch.setPromptText("Search product name or code...");
        posSearch.textProperty().addListener((o, oldV, newV) -> filterCatalog());
        HBox.setHgrow(posSearch, Priority.ALWAYS);

        posCategory = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        posCategory.setValue("All");
        posCategory.setOnAction(e -> filterCatalog());

        TableView<Product> catalogTable = new TableView<>(catalogData);
        catalogTable.getColumns().addAll(List.of(
                col("Code", 60, Product::barcode),
                col("Product", 165, Product::name),
                col("Price", 70, p -> String.format("Rs.%.0f", p.price())),
                col("Stock", 55, Product::stock),
                col("GST%", 55, Product::gst)
        ));
        catalogTable.setRowFactory(tv -> {
            TableRow<Product> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) addToCart(row.getItem(), getSpinnerQty());
            });
            return row;
        });

        qtySpinner = new Spinner<>(1, 99, 1);
        qtySpinner.setPrefWidth(75);
        qtySpinner.setEditable(true);

        Button addBtn = new Button("Add to Cart");
        addBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setOnAction(e -> {
            Product sel = catalogTable.getSelectionModel().getSelectedItem();
            if (sel == null) alert("Select Product", "Select a product from the catalog first (or double-click a row).");
            else addToCart(sel, getSpinnerQty());
        });

        VBox left = new VBox(8, new Label("1. Select Products (Double-Click or Scan)"),
                barcodeField, new HBox(8, posSearch, posCategory), catalogTable,
                new HBox(8, new Label("Qty:"), qtySpinner, addBtn));
        left.setPadding(new Insets(10));
        VBox.setVgrow(catalogTable, Priority.ALWAYS);

        // Center: Customer & Shopping Cart
        custName = new TextField(); custName.setPromptText("Customer Name");
        custPhone = new TextField(); custPhone.setPromptText("Phone Number");
        custName.textProperty().addListener((o, ov, nv) -> updateCartAndReceipt("DRAFT"));
        custPhone.textProperty().addListener((o, ov, nv) -> updateCartAndReceipt("DRAFT"));

        cartTable = new TableView<>(cartData);
        cartTable.getColumns().addAll(List.of(
                col("Item", 150, c -> c.product.name()),
                col("Qty", 45, c -> c.qty),
                col("Price", 65, c -> String.format("%.2f", c.product.price())),
                col("GST", 60, c -> String.format("%.2f", c.tax())),
                col("Total", 75, c -> String.format("%.2f", c.total()))
        ));

        Button plusBtn = new Button("+1");
        plusBtn.setOnAction(e -> changeCartQty(1));
        Button minusBtn = new Button("-1");
        minusBtn.setOnAction(e -> changeCartQty(-1));
        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(e -> {
            CartItem sel = cartTable.getSelectionModel().getSelectedItem();
            if (sel != null) { cartData.remove(sel); updateCartAndReceipt("DRAFT"); }
        });
        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> { cartData.clear(); updateCartAndReceipt("DRAFT"); });

        discField = new TextField("0"); discField.setPrefWidth(50);
        discField.textProperty().addListener((o, oldV, newV) -> updateCartAndReceipt("DRAFT"));
        payMode = new ComboBox<>(FXCollections.observableArrayList("UPI", "Cash", "Card"));
        payMode.setValue("UPI");
        payMode.setOnAction(e -> updateCartAndReceipt("DRAFT"));

        totalLabel = new Label("Grand Total: Rs. 0.00");
        totalLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

        Button checkoutBtn = new Button("Generate Bill & Save to Database");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.setStyle("-fx-background-color: #1e40af; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 9;");
        checkoutBtn.setOnAction(e -> performCheckout());

        VBox center = new VBox(8, new Label("2. Customer & Shopping Cart"),
                new HBox(8, custName, custPhone), cartTable,
                new HBox(6, plusBtn, minusBtn, removeBtn, clearBtn, new Label("Disc%:"), discField, payMode),
                totalLabel, checkoutBtn);
        center.setPadding(new Insets(10));
        VBox.setVgrow(cartTable, Priority.ALWAYS);

        // Right: Receipt Preview
        receiptArea = new TextArea();
        receiptArea.setEditable(false);
        receiptArea.setFont(Font.font("Monospaced", 12));
        Button saveTxtBtn = new Button("Save Receipt (.txt)");
        saveTxtBtn.setMaxWidth(Double.MAX_VALUE);
        saveTxtBtn.setOnAction(e -> saveTextFile(receiptArea.getText()));

        VBox right = new VBox(8, new Label("3. Bill Receipt Preview"), receiptArea, saveTxtBtn);
        right.setPadding(new Insets(10));
        VBox.setVgrow(receiptArea, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, center, right);
        split.setDividerPositions(0.36, 0.70);
        return split;
    }

    // --- TAB 2: INVENTORY CRUD ---
    private BorderPane buildInventoryTab() {
        TableView<Product> table = new TableView<>(inventoryData);
        table.getColumns().addAll(List.of(
                col("ID", 50, Product::id),
                col("Barcode", 90, Product::barcode),
                col("Product Name", 220, Product::name),
                col("Category", 120, Product::category),
                col("Price (Rs.)", 95, p -> String.format("%.2f", p.price())),
                col("Stock", 80, Product::stock),
                col("GST %", 75, Product::gst),
                col("Status", 110, p -> p.stock() == 0 ? "OUT OF STOCK" : (p.stock() <= 15 ? "LOW STOCK" : "In Stock"))
        ));

        TextField fCode = new TextField(), fName = new TextField(), fPrice = new TextField(), fStock = new TextField();
        ComboBox<String> fCat = new ComboBox<>(FXCollections.observableArrayList("Dairy", "Grains", "Beverages", "Snacks", "Household"));
        fCat.setValue("Dairy");
        fCat.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Double> fGst = new ComboBox<>(FXCollections.observableArrayList(0.0, 5.0, 12.0, 18.0, 28.0));
        fGst.setValue(5.0);
        fGst.setMaxWidth(Double.MAX_VALUE);

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, p) -> {
            if (p != null) {
                fCode.setText(p.barcode()); fName.setText(p.name()); fCat.setValue(p.category());
                fPrice.setText(String.valueOf(p.price())); fStock.setText(String.valueOf(p.stock())); fGst.setValue(p.gst());
            }
        });

        Runnable clearForm = () -> {
            table.getSelectionModel().clearSelection();
            fCode.clear(); fName.clear(); fPrice.clear(); fStock.clear();
        };

        Button addBtn = new Button("Add New Product");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-font-weight: bold;");
        addBtn.setOnAction(e -> {
            if (saveProductForm(0, fCode, fName, fCat, fPrice, fStock, fGst)) clearForm.run();
        });

        Button updBtn = new Button("Update Selected");
        updBtn.setMaxWidth(Double.MAX_VALUE);
        updBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold;");
        updBtn.setOnAction(e -> {
            Product sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) alert("No Selection", "Select a product from the table first to update it.");
            else saveProductForm(sel.id(), fCode, fName, fCat, fPrice, fStock, fGst);
        });

        Button delBtn = new Button("Delete Selected");
        delBtn.setMaxWidth(Double.MAX_VALUE);
        delBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold;");
        delBtn.setOnAction(e -> {
            Product sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) { alert("No Selection", "Select a product from the table to delete."); return; }
            try { dao.deleteProduct(sel.id()); clearForm.run(); refreshAll(); }
            catch (Exception ex) { alert("Error", ex.getMessage()); }
        });

        Button clrBtn = new Button("Clear Form");
        clrBtn.setMaxWidth(Double.MAX_VALUE);
        clrBtn.setOnAction(e -> clearForm.run());

        VBox form = new VBox(8, new Label("Product Form (CRUD)"),
                new Label("Barcode:"), fCode, new Label("Name:"), fName,
                new Label("Category:"), fCat, new Label("Price (Rs.):"), fPrice,
                new Label("Stock:"), fStock, new Label("GST %:"), fGst,
                new Separator(), addBtn, updBtn, delBtn, clrBtn);
        form.setPadding(new Insets(12));
        form.setPrefWidth(280);

        BorderPane pane = new BorderPane(table);
        pane.setRight(form);
        pane.setPadding(new Insets(10));
        return pane;
    }

    // --- TAB 3: SALES REPORTS & HISTORY ---
    private BorderPane buildReportsTab() {
        statsBanner = new Label();
        statsBanner.setFont(Font.font("System", FontWeight.BOLD, 15));
        statsBanner.setPadding(new Insets(10));

        salesPieChart = new PieChart();
        salesPieChart.setTitle("Revenue by Category");
        salesPieChart.setAnimated(false);

        billTable = new TableView<>(billHistoryData);
        billTable.getColumns().addAll(List.of(
                col("Invoice #", 125, Bill::billNo),
                col("Customer", 130, Bill::customer),
                col("Total (Rs.)", 95, b -> String.format("%.2f", b.grandTotal())),
                col("Mode", 65, Bill::paymentMode),
                col("Date", 145, Bill::date)
        ));

        historyReceiptArea = new TextArea();
        historyReceiptArea.setEditable(false);
        historyReceiptArea.setFont(Font.font("Monospaced", 12));

        billTable.getSelectionModel().selectedItemProperty().addListener((obs, o, b) -> {
            if (b != null) {
                historyReceiptArea.setText(formatReceipt(b.billNo(), b.date(), b.customer(), b.phone(),
                        b.paymentMode(), dao.getBillItems(b.id()), b.subtotal(), b.discount(), b.tax(), b.grandTotal()));
            }
        });

        SplitPane split = new SplitPane(salesPieChart, billTable, historyReceiptArea);
        split.setDividerPositions(0.32, 0.68);

        BorderPane pane = new BorderPane(split);
        pane.setTop(statsBanner);
        pane.setPadding(new Insets(10));
        return pane;
    }

    // --- HELPER METHODS ---
    private int getSpinnerQty() {
        try { return Math.max(1, Integer.parseInt(qtySpinner.getEditor().getText().trim())); }
        catch (Exception e) { return qtySpinner.getValue(); }
    }

    private void addToCart(Product p, int qty) {
        if (p.stock() <= 0) { alert("Out of Stock", "'" + p.name() + "' is currently out of stock."); return; }
        for (CartItem ci : cartData) {
            if (ci.product.id() == p.id()) {
                if (ci.qty + qty > p.stock()) { alert("Stock Limit", "Only " + p.stock() + " units available."); return; }
                ci.qty += qty;
                cartTable.refresh();
                updateCartAndReceipt("DRAFT");
                return;
            }
        }
        if (qty > p.stock()) { alert("Stock Limit", "Only " + p.stock() + " units available."); return; }
        cartData.add(new CartItem(p, qty));
        updateCartAndReceipt("DRAFT");
    }

    private void changeCartQty(int delta) {
        CartItem sel = cartTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        int next = sel.qty + delta;
        if (next <= 0) cartData.remove(sel);
        else if (next > sel.product.stock()) alert("Stock Limit", "Only " + sel.product.stock() + " units available.");
        else { sel.qty = next; cartTable.refresh(); }
        updateCartAndReceipt("DRAFT");
    }

    private double[] computeCartTotals() {
        double sub = cartData.stream().mapToDouble(CartItem::gross).sum();
        double rawTax = cartData.stream().mapToDouble(CartItem::tax).sum();
        double discPct = 0;
        try { discPct = Math.max(0, Math.min(50, Double.parseDouble(discField.getText().trim()))); } catch (Exception ignored) {}
        double disc = sub * (discPct / 100.0);
        double tax = rawTax * (1.0 - discPct / 100.0);
        double grand = Math.max(0, sub - disc) + tax;
        return new double[]{sub, disc, tax, grand};
    }

    private void updateCartAndReceipt(String billNo) {
        double[] t = computeCartTotals();
        totalLabel.setText(String.format("Grand Total: Rs. %.2f", t[3]));
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        receiptArea.setText(formatReceipt(billNo, now, custName.getText(), custPhone.getText(),
                payMode.getValue(), cartData, t[0], t[1], t[2], t[3]));
    }

    private void performCheckout() {
        if (cartData.isEmpty()) { alert("Empty Cart", "Add products to the cart before checkout."); return; }
        double[] t = computeCartTotals();
        try {
            String billNo = dao.saveBillTransaction(cartData, custName.getText(), custPhone.getText(),
                    t[0], t[1], t[2], t[3], payMode.getValue());
            updateCartAndReceipt(billNo);
            String finalReceipt = receiptArea.getText();
            cartData.clear();
            custName.clear();
            custPhone.clear();
            discField.setText("0");
            refreshAll();
            receiptArea.setText(finalReceipt);
            alert("Success", "Saved Invoice " + billNo + " (Rs. " + String.format("%.2f", t[3]) + ")");
        } catch (Exception ex) {
            alert("Checkout Failed", ex.getMessage());
        }
    }

    private boolean saveProductForm(int id, TextField code, TextField name, ComboBox<String> cat,
                                    TextField price, TextField stock, ComboBox<Double> gst) {
        if (code.getText().isBlank() || name.getText().isBlank()) {
            alert("Missing Fields", "Barcode and Product Name are required.");
            return false;
        }
        try {
            double pr = Double.parseDouble(price.getText().trim());
            int st = Integer.parseInt(stock.getText().trim());
            if (pr < 0 || st < 0) { alert("Invalid Values", "Price and Stock cannot be negative."); return false; }
            dao.saveProduct(new Product(id, code.getText().trim(), name.getText().trim(), cat.getValue(), pr, st, gst.getValue()));
            refreshAll();
            return true;
        } catch (NumberFormatException ex) {
            alert("Invalid Input", "Enter a valid number for Price and Stock.");
            return false;
        } catch (Exception ex) {
            alert("Database Error", ex.getMessage());
            return false;
        }
    }

    private String formatReceipt(String no, String date, String cust, String phone, String mode,
                                 List<CartItem> items, double sub, double disc, double tax, double grand) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("       SUPERMARKET BILLING SYSTEM       \n");
        sb.append("========================================\n");
        sb.append(String.format("Invoice : %s\nDate    : %s\nCustomer: %s (%s)\nPayment : %s\n",
                no, date, cust.isBlank() ? "Walk-in" : cust.trim(), phone.isBlank() ? "-" : phone.trim(), mode));
        sb.append("----------------------------------------\n");
        sb.append(String.format("%-18s %3s %7s %8s\n", "ITEM", "QTY", "PRICE", "TOTAL"));
        sb.append("----------------------------------------\n");
        for (CartItem c : items) {
            String n = c.product.name().length() > 18 ? c.product.name().substring(0, 18) : c.product.name();
            sb.append(String.format("%-18s %3d %7.2f %8.2f\n", n, c.qty, c.product.price(), c.total()));
        }
        sb.append("----------------------------------------\n");
        sb.append(String.format("Subtotal   : Rs. %.2f\nDiscount   : -Rs. %.2f\nGST        : Rs. %.2f\n", sub, disc, tax));
        sb.append(String.format("GRAND TOTAL: Rs. %.2f\n", grand));
        sb.append("========================================\n");
        return sb.toString();
    }

    private void filterCatalog() {
        String kw = posSearch == null ? "" : posSearch.getText().trim().toLowerCase();
        String cat = posCategory == null ? "All" : posCategory.getValue();
        catalogData.setAll(inventoryData.stream().filter(p ->
                (kw.isEmpty() || p.name().toLowerCase().contains(kw) || p.barcode().toLowerCase().contains(kw))
                && ("All".equals(cat) || p.category().equalsIgnoreCase(cat))
        ).toList());
    }

    private void refreshAll() {
        inventoryData.setAll(dao.getAllProducts());
        filterCatalog();
        billHistoryData.setAll(dao.getBills());
        if (!billHistoryData.isEmpty() && billTable.getSelectionModel().getSelectedItem() == null) {
            billTable.getSelectionModel().selectFirst();
        }
        double[] s = dao.getSummaryStats();
        statsBanner.setText(String.format("Total Invoices: %.0f   |   Total Revenue: Rs. %.2f   |   Avg Bill Value: Rs. %.2f", s[0], s[1], s[2]));

        ObservableList<PieChart.Data> pie = FXCollections.observableArrayList();
        dao.getCategorySales().forEach((k, v) -> pie.add(new PieChart.Data(k + " (Rs." + Math.round(v) + ")", v)));
        salesPieChart.setData(pie);
    }

    private <T> TableColumn<T, String> col(String title, double width, Function<T, Object> mapper) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(mapper.apply(d.getValue()))));
        return c;
    }

    private void saveTextFile(String content) {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("Receipt.txt");
        File f = fc.showSaveDialog(null);
        if (f != null) {
            try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
            catch (Exception ex) { alert("Error", ex.getMessage()); }
        }
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title); a.setHeaderText(null); a.showAndWait();
    }

    public static void main(String[] args) { launch(args); }
}
