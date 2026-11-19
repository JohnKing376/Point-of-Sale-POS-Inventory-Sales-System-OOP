package pos.domain.product;

import pos.domain.Money;
import pos.domain.exceptions.InsufficientStockException;

public class Product {
    private final int id;
    private final String name;
    private final Category category;
    private long price; 
    private int stockQuantity;

    public Product(int id, String name, Category category, long price, int stockQuantity) {
        if (id <= 0) {
            throw new IllegalArgumentException("ID must be greater than 0");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank");
        }
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }

        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPrice() {
        return price;
    }

    public Category getCategory() {
        return category;    
    }   

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void increaseStock(int qty) {
        requirePositive(qty);
        stockQuantity += qty;
    }

    public void decreaseStock(int qty) {
        requirePositive(qty);
        if (qty > stockQuantity) {
            throw new InsufficientStockException(
                "Cannot remove " + qty + " of '" + name + "': only " + stockQuantity + " in stock"
            );
        }
        stockQuantity -= qty;
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
    }

    @Override
    public String toString() {
        return String.format("#%d %-20s %-12s %12s  (stock: %d)",
                id, name, category, Money.naira(price), stockQuantity);
    }
}