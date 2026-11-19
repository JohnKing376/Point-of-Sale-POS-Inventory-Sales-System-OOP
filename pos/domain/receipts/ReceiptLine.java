package pos.domain.receipts;

public record ReceiptLine(String productName, int quantity, long unitPrice, long lineTotal) {}