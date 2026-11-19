package pos.domain.order;

import pos.domain.pricing.interfaces.PricedLine;
import pos.domain.product.Category;
import pos.domain.product.Product;

public class OrderItem implements PricedLine {
    private final Product product;
    private final long unitPrice;
    private int quantity;

    OrderItem(Product product, int quantity) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }

        this.product = product;
        this.unitPrice = product.getPrice();
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    @Override
    public int getProductId() {
        return product.getId();
    }

    @Override
    public Category getCategory() {
        return product.getCategory();
    }

    @Override
    public int getQuantity() {
        return quantity;
    }

    @Override
    public long getUnitPrice() {
        return unitPrice;
    }

    @Override
    public long lineTotal() {
        return unitPrice * quantity;
    }

    void increaseQuantity(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Quantity to increase must be greater than 0");
        }
        quantity += qty;
    }
}
