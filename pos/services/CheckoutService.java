package pos.services;

import pos.domain.exceptions.InsufficientStockException;
import pos.domain.order.Order;
import pos.domain.order.OrderItem;
import pos.domain.order.OrderStatus;
import pos.domain.product.Inventory;
import pos.domain.receipts.Receipt;

public class CheckoutService {
    private final Inventory inventory;

    public CheckoutService(Inventory inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Inventory cannot be null");
        }
        this.inventory = inventory;
    }

    public Receipt checkout(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new IllegalStateException("Only open orders can be checked out");
        }
        if (order.isEmpty()) {
            throw new IllegalStateException("Cannot check out an empty order");
        }

        for (OrderItem item : order.getItems()) {
            int productId = item.getProduct().getId();
            int qty = item.getQuantity();

            if (!inventory.isInStock(productId, qty)) {
                throw new InsufficientStockException(
                        "Not enough " + item.getProduct().getName() + ": requested " + qty
                                + ", available " + item.getProduct().getStockQuantity()
                );
            }
        }

        for (OrderItem item : order.getItems()) {
            inventory.removeStock(item.getProduct().getId(), item.getQuantity());
        }

        order.markPaid();

        return new Receipt(order);
    }
}