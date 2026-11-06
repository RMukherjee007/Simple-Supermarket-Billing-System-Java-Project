# Supermarket Billing System

A concise, modular **Supermarket Billing and Inventory Management System** built with **JavaFX**, **Event Handling**, and **MySQL JDBC** to satisfy the **Outstanding (Max. 15/15 Marks)** rubric.

---

## Project Structure (4 Simple Java Files)

```text
Java_Project/
├── pom.xml                                        # Maven configuration (JavaFX 21 + MySQL Connector/J)
├── run.sh                                         # One-command compile & launch script
├── sql/schema.sql                                 # MySQL schema (products, bills, bill_items)
├── VIVA_GUIDE.md                                  # Viva (15M) & Descriptive Test (5M) Q&A Guide
└── src/main/java/com/supermarket/
    ├── Models.java                                # Compact Java records & models (Product, Bill, CartItem)
    ├── DBConnection.java                          # MySQL JDBC connection & automatic table initializer
    ├── SupermarketDAO.java                        # CRUD, Atomic JDBC Transactions, Joins & Aggregations
    └── SupermarketApp.java                        # JavaFX GUI & Event Handling (POS, Inventory, Reports)
```

---

## How to Run
```bash
./run.sh
```
*(To pass a custom MySQL password: `DB_USER=root DB_PASS=your_password ./run.sh`)*
