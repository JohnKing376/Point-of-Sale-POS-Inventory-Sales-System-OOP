package pos.domain.receipts;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import pos.domain.order.Order;
import pos.domain.order.OrderItem;

public class Receipt {
    private final int orderId;
    private final LocalDateTime issuedAt;
    private final List<ReceiptLine> lines;
    private final long subTotal;
    private final long discountAmount;
    private final long taxAmount;
    private final long total;
    private final String discountDescription;
    private final String taxDescription;

    public Receipt(Order order) {
        this(order, Clock.systemDefaultZone());
    }

    public Receipt(Order order, Clock clock) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock cannot be null");
        }

        List<ReceiptLine> builtLines = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            builtLines.add(new ReceiptLine(
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.lineTotal()
            ));
        }

        this.orderId = order.getId();
        this.issuedAt = LocalDateTime.now(clock);
        this.lines = List.copyOf(builtLines);
        this.subTotal = order.subTotal();
        this.discountAmount = order.discountTotal();
        this.taxAmount = order.taxTotal();
        this.total = order.total();
        this.discountDescription = order.getDiscount().describe();
        this.taxDescription = order.getTaxPolicy().describe();
    }

    public int getOrderId() { return orderId; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public List<ReceiptLine> getLines() { return lines; }
    public long getSubTotal() { return subTotal; }
    public long getDiscountAmount() { return discountAmount; }
    public long getTaxAmount() { return taxAmount; }
    public long getTotal() { return total; }
    public String getDiscountDescription() { return discountDescription; }
    public String getTaxDescription() { return taxDescription; }
}
