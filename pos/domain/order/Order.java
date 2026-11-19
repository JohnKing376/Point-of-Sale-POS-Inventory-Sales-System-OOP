package pos.domain.order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pos.domain.pricing.discount.NoDiscount;
import pos.domain.pricing.interfaces.Discount;
import pos.domain.pricing.interfaces.PricedLine;
import pos.domain.pricing.interfaces.TaxPolicy;
import pos.domain.pricing.tax.FlatVat;
import pos.domain.product.Product;

public class Order {
    private final int id;
    private final List<OrderItem> items;
    private OrderStatus status;

    private Discount discount = new NoDiscount();
    private TaxPolicy taxPolicy = new FlatVat();

    public Order(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Order ID must be greater than 0");
        }
        this.id = id;
        this.items = new ArrayList<>();
        this.status = OrderStatus.OPEN;
    }

    private Optional<OrderItem> findItemByProduct(int productId) {
        for (OrderItem item : items) {
            if (item.getProductId() == productId) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public void addItem(Product product, int qty) {
        if (status != OrderStatus.OPEN) {
            throw new IllegalStateException("Cannot add items to a non-open order");
        }

        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }

        Optional<OrderItem> existingItem = findItemByProduct(product.getId());
        if (existingItem.isPresent()) {
            existingItem.get().increaseQuantity(qty);
        } else {
            items.add(new OrderItem(product, qty));
        }
    }

    public void removeItem(int productId) {
        if (status != OrderStatus.OPEN) {
            throw new IllegalStateException("Cannot remove items from a non-open order");
        }

        Optional<OrderItem> itemToRemove = findItemByProduct(productId);
        if (itemToRemove.isPresent()) {
            items.remove(itemToRemove.get());
        } else {
            throw new IllegalArgumentException("Product not found in order");
        }
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public int quantityOf(int productId) {
        return findItemByProduct(productId).map(OrderItem::getQuantity).orElse(0);
    }

    public long subTotal() {
        long total = 0;

        for (OrderItem item : items) {
            total += item.lineTotal();
        }
        return total;
    }

    public int getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void markPaid() {
        if (status != OrderStatus.OPEN) {
            throw new IllegalStateException("Only open orders can be marked as paid");
        }

        if (isEmpty()) {
            throw new IllegalStateException("Cannot mark an empty order as paid");
        }

        status = OrderStatus.PAID;
    }

    public void cancel() {
        if (status == OrderStatus.PAID) {
            throw new IllegalStateException("Paid orders cannot be cancelled");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    public void applyDiscount(Discount discount) {
        if (status != OrderStatus.OPEN) {
            throw new IllegalStateException("Only open orders can apply a discount");
        }
        if (discount == null) {
            throw new IllegalArgumentException("Discount cannot be null");
        }
        this.discount = discount;
    }

    public void setTaxPolicy(TaxPolicy taxPolicy) {
        if (status != OrderStatus.OPEN) {
            throw new IllegalStateException("Only open orders can change the tax policy");
        }
        if (taxPolicy == null) {
            throw new IllegalArgumentException("Tax Policy cannot be null");
        }

        this.taxPolicy = taxPolicy;
    }

    private Map<OrderItem, Long> lineDiscounts() {
        Map<PricedLine, Long> amounts = discount.amountsOff(getItems());
        if (amounts == null) {
            throw new IllegalStateException("Discount '" + discount.describe() + "' returned no amounts");
        }
        for (PricedLine line : amounts.keySet()) {
            if (!items.contains(line)) {
                throw new IllegalStateException("Discount '" + discount.describe()
                        + "' returned an amount for a line that is not in this order");
            }
        }

        Map<OrderItem, Long> checked = new HashMap<>();
        for (OrderItem item : items) {
            Long amount = amounts.getOrDefault(item, 0L);
            if (amount == null || amount < 0 || amount > item.lineTotal()) {
                throw new IllegalStateException("Discount '" + discount.describe()
                        + "' returned an invalid amount for " + item.getProduct().getName());
            }
            checked.put(item, amount);
        }
        return checked;
    }

    public long discountTotal() {
        long total = 0;
        for (long amount : lineDiscounts().values()) {
            total += amount;
        }
        return total;
    }

    public long taxTotal() {
        Map<OrderItem, Long> discounts = lineDiscounts();
        long total = 0;

        for (OrderItem item : items) {
            total += taxPolicy.taxFor(item, item.lineTotal() - discounts.get(item));
        }
        return total;
    }

    public long total() {
        return subTotal() - discountTotal() + taxTotal();
    }

    public Discount getDiscount() {
        return this.discount;
    }

    public TaxPolicy getTaxPolicy() {
        return this.taxPolicy;
    }
}
