package pos;

import java.util.Set;

import pos.domain.Money;
import pos.domain.order.Order;
import pos.domain.order.OrderItem;
import pos.domain.pricing.discount.BuyXGetYFree;
import pos.domain.pricing.discount.FixedAmountDiscount;
import pos.domain.pricing.discount.NoDiscount;
import pos.domain.pricing.discount.PercentageDiscount;
import pos.domain.pricing.tax.CategoryTax;
import pos.domain.pricing.tax.FlatVat;
import pos.domain.pricing.tax.TaxExempt;
import pos.domain.product.Category;
import pos.domain.product.Inventory;
import pos.domain.product.Product;

public class Demo {

    public static void main(String[] args) {
        Inventory inventory = new Inventory();
        SampleData.seed(inventory);

        runInventoryTests(inventory);
        runOrderTests(inventory);
        runPricingTests(inventory);
    }

    private static void runInventoryTests(Inventory inventory) {
        printSection("Initial inventory");
        printAll(inventory);

        printSection("After restocking #1 (+20) and selling 2 of #3");
        inventory.restock(1, 20);
        inventory.removeStock(3, 2);
        printAll(inventory);

        printSection("Stock checks");
        System.out.println("  10 of #1 available?  " + inventory.isInStock(1, 10));
        System.out.println("  100 of #4 available? " + inventory.isInStock(4, 100));
        System.out.println("  Unknown #99?         " + inventory.isInStock(99, 1));

        printSection("Inventory failure cases");
        tryAction("Duplicate ID",
                () -> inventory.addProduct(new Product(1, "Duplicate", Category.OTHER, 100, 1)));
        tryAction("Remove more than available",
                () -> inventory.removeStock(4, 999));
        System.out.println("  -> #4 after failed removal: " + inventory.getById(4));
        tryAction("Unknown product ID",
                () -> inventory.getById(99));
        tryAction("Zero quantity restock",
                () -> inventory.restock(1, 0));
        tryAction("Negative price",
                () -> new Product(10, "Bad Item", Category.OTHER, -500, 1));
        tryAction("Null name",
                () -> new Product(11, null, Category.OTHER, 500, 1));
        tryAction("Null category",
                () -> new Product(12, "No Category", null, 500, 1));
        tryAction("Modify the list from listAll()",
                () -> inventory.listAll().clear());
    }

    private static void runOrderTests(Inventory inventory) {
        Product indomie = inventory.getById(1);
        Product speaker = inventory.getById(3);
        Product shirt = inventory.getById(5);

        printSection("Order: building a basket");
        Order order = new Order(1);
        order.addItem(indomie, 3);
        order.addItem(speaker, 1);
        order.addItem(shirt, 2);
        printOrder(order);

        printSection("Order: adding 2 more Indomie (should merge into one line)");
        order.addItem(indomie, 2);
        printOrder(order);

        printSection("Order: removing the shirt");
        order.removeItem(shirt.getId());
        printOrder(order);

        printSection("Order failure cases (open order)");
        tryAction("Zero quantity", () -> order.addItem(indomie, 0));
        tryAction("Null product", () -> order.addItem(null, 1));
        tryAction("Remove product not in order", () -> order.removeItem(99));
        tryAction("Modify the list from getItems()", () -> order.getItems().clear());

        Order emptyOrder = new Order(2);
        tryAction("Pay an empty order", emptyOrder::markPaid);

        printSection("Order: paying order #1");
        order.markPaid();
        printOrder(order);

        printSection("Order failure cases (paid order)");
        tryAction("Add to a paid order", () -> order.addItem(indomie, 1));
        tryAction("Remove from a paid order", () -> order.removeItem(indomie.getId()));
        tryAction("Cancel a paid order", order::cancel);
        tryAction("Pay an already-paid order", order::markPaid);

        printSection("Order: cancelling a fresh order");
        Order toCancel = new Order(3);
        toCancel.addItem(speaker, 1);
        toCancel.cancel();
        System.out.println("  Order #3 status: " + toCancel.getStatus());
        tryAction("Cancel it again", toCancel::cancel);
        tryAction("Invalid order ID", () -> new Order(0));
    }

    private static void runPricingTests(Inventory inventory) {
        Product indomie = inventory.getById(1);
        Product speaker = inventory.getById(3);
        Product charger = inventory.getById(4);

        Order order = new Order(10);
        order.addItem(indomie, 7);
        order.addItem(speaker, 1);
        order.addItem(charger, 2);

        printSection("Pricing 1: defaults (no discount, flat VAT 7.5%)");
        printOrder(order);
        printTotals(order);
        expectTotal(order, 2_553_125);

        printSection("Pricing 2: 10% off + flat VAT");
        order.applyDiscount(PercentageDiscount.ofPercent(10));
        order.setTaxPolicy(new FlatVat());
        printTotals(order);
        expectTotal(order, 2_297_813);

        printSection("Pricing 3: ₦5,000 off + VAT with FOOD exempt");
        order.applyDiscount(new FixedAmountDiscount(500000));
        order.setTaxPolicy(new CategoryTax(Set.of(Category.FOOD)));
        printTotals(order);
        expectTotal(order, 2_005_263);

        printSection("Pricing 4: buy 2 get 1 free on Indomie (7 in basket -> 2 free)");
        order.applyDiscount(new BuyXGetYFree(indomie.getId(), 2, 1));
        order.setTaxPolicy(new FlatVat());
        printTotals(order);
        expectTotal(order, 2_499_375);

        printSection("Pricing 5: discount bigger than subtotal (caps at subtotal, so no tax on a free sale)");
        order.applyDiscount(new FixedAmountDiscount(100_000_000));
        printTotals(order);
        expectTotal(order, 0);

        printSection("Pricing 6: 10% off + tax exempt");
        order.applyDiscount(PercentageDiscount.ofPercent(10));
        order.setTaxPolicy(new TaxExempt());
        printTotals(order);
        expectTotal(order, 2_137_500);

        printSection("Pricing failure cases");
        tryAction("0% discount", () -> PercentageDiscount.ofPercent(0));
        tryAction("150% discount", () -> PercentageDiscount.ofPercent(150));
        tryAction("Negative fixed discount", () -> new FixedAmountDiscount(-100));
        tryAction("Buy 0 get 1", () -> new BuyXGetYFree(1, 0, 1));
        tryAction("Negative VAT rate", () -> new FlatVat(-1));
        tryAction("VAT over 100%", () -> new FlatVat(20000));
        tryAction("Null exempt set", () -> new CategoryTax(null));
        tryAction("Null discount", () -> order.applyDiscount(null));
        tryAction("Null tax policy", () -> order.setTaxPolicy(null));

        Order paid = new Order(11);
        paid.addItem(indomie, 1);
        paid.markPaid();
        tryAction("Discount on a paid order", () -> paid.applyDiscount(new NoDiscount()));
        tryAction("Tax change on a paid order", () -> paid.setTaxPolicy(new TaxExempt()));
    }

    private static void printAll(Inventory inventory) {
        for (Product product : inventory.listAll()) {
            System.out.println("  " + product);
        }
    }

    private static void printOrder(Order order) {
        System.out.println("  Order #" + order.getId() + " [" + order.getStatus() + "]");
        for (OrderItem item : order.getItems()) {
            System.out.printf("    %-20s x%-3d @ %12s = %12s%n",
                    item.getProduct().getName(),
                    item.getQuantity(),
                    Money.naira(item.getUnitPrice()),
                    Money.naira(item.lineTotal()));
        }
        System.out.printf("    %-41s %12s%n", "Subtotal", Money.naira(order.subTotal()));
    }

    private static void printTotals(Order order) {
        System.out.printf("    %-41s %12s%n", "Subtotal", Money.naira(order.subTotal()));
        System.out.printf("    %-41s %12s%n",
                "Discount (" + order.getDiscount().describe() + ")", "-" + Money.naira(order.discountTotal()));
        System.out.printf("    %-41s %12s%n",
                "Tax (" + order.getTaxPolicy().describe() + ")", "+" + Money.naira(order.taxTotal()));
        System.out.printf("    %-41s %12s%n", "TOTAL", Money.naira(order.total()));
    }

    private static void expectTotal(Order order, long expectedKobo) {
        long actual = order.total();
        String verdict = actual == expectedKobo ? "OK" : "MISMATCH (expected " + Money.naira(expectedKobo) + ")";
        System.out.println("    Check: total " + Money.naira(actual) + " -> " + verdict);
    }

    private static void printSection(String title) {
        System.out.println("\n=== " + title + " ===");
    }

    private static void tryAction(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": no error (UNEXPECTED)");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}