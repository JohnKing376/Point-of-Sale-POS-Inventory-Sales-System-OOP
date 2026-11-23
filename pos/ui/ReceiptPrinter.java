package pos.ui;

import java.time.format.DateTimeFormatter;

import pos.domain.Money;
import pos.domain.receipts.Receipt;
import pos.domain.receipts.ReceiptLine;

public class ReceiptPrinter {
    private static final String STORE_NAME = AppInfo.STORE_NAME;
    private static final int WIDTH = 50;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String DOUBLE_LINE = "=".repeat(WIDTH);
    private static final String SINGLE_LINE = "-".repeat(WIDTH);

    public void print(Receipt receipt) {
        System.out.print(format(receipt));
    }

    public String format(Receipt receipt) {
        StringBuilder out = new StringBuilder();
        line(out, DOUBLE_LINE);
        line(out, center(STORE_NAME, WIDTH));
        line(out, String.format("Order #%-20d %21s",
                receipt.getOrderId(), receipt.getIssuedAt().format(DATE_FORMAT)));
        line(out, SINGLE_LINE);

        for (ReceiptLine item : receipt.getLines()) {
            line(out, String.format("%-20s x%-3d %11s %12s",
                    item.productName(), item.quantity(),
                    Money.naira(item.unitPrice()), Money.naira(item.lineTotal())));
        }

        line(out, SINGLE_LINE);
        row(out, "Subtotal", Money.naira(receipt.getSubTotal()));
        row(out, "Discount (" + receipt.getDiscountDescription() + ")",
                "-" + Money.naira(receipt.getDiscountAmount()));
        row(out, "Tax (" + receipt.getTaxDescription() + ")",
                "+" + Money.naira(receipt.getTaxAmount()));
        line(out, SINGLE_LINE);
        row(out, "TOTAL", Money.naira(receipt.getTotal()));
        line(out, DOUBLE_LINE);
        return out.toString();
    }

    private static void row(StringBuilder out, String label, String amount) {
        line(out, String.format("%-36s %13s", label, amount));
    }

    private static void line(StringBuilder out, String text) {
        out.append(text).append(System.lineSeparator());
    }

    private static String center(String text, int width) {
        int padding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(padding) + text;
    }
}
