package pos.ui;

import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

import pos.domain.Money;
import pos.domain.order.Order;
import pos.domain.order.OrderItem;
import pos.domain.pricing.discount.BuyXGetYFree;
import pos.domain.pricing.discount.FixedAmountDiscount;
import pos.domain.pricing.discount.NoDiscount;
import pos.domain.pricing.discount.PercentageDiscount;
import pos.domain.pricing.interfaces.Discount;
import pos.domain.pricing.interfaces.TaxPolicy;
import pos.domain.pricing.tax.CategoryTax;
import pos.domain.pricing.tax.FlatVat;
import pos.domain.pricing.tax.TaxExempt;
import pos.domain.product.Category;
import pos.domain.product.Inventory;
import pos.domain.product.Product;
import pos.domain.receipts.Receipt;
import pos.services.CheckoutService;

public class ConsoleApp {
    private final Inventory inventory;
    private final CheckoutService checkoutService;
    private final ReceiptPrinter receiptPrinter;
    private final Scanner scanner = new Scanner(System.in);

    private Order currentOrder;
    private int nextOrderId = 1;

    public ConsoleApp(Inventory inventory, CheckoutService checkoutService, ReceiptPrinter receiptPrinter) {
        if (inventory == null || checkoutService == null || receiptPrinter == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.inventory = inventory;
        this.checkoutService = checkoutService;
        this.receiptPrinter = receiptPrinter;
    }

    public void run() {
        System.out.println("Welcome to " + AppInfo.NAME);
        boolean running = true;

        while (running) {
            printMenu();

            try {
                switch (readInt("Choose an option: ")) {
                    case 1 -> listProducts();
                    case 2 -> startOrder();
                    case 3 -> addItem();
                    case 4 -> removeItem();
                    case 5 -> viewOrder();
                    case 6 -> applyDiscount();
                    case 7 -> setTaxPolicy();
                    case 8 -> checkout();
                    case 9 -> cancelOrder();
                    case 10 -> restock();
                    case 0 -> running = false;
                    default -> System.out.println("Invalid option.");
                }
            } catch (NoSuchElementException e) {
                running = false;
            } catch (RuntimeException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Goodbye!");
    }

    private void printMenu() {
        String orderInfo = (currentOrder == null)
                ? "no open order"
                : "order #" + currentOrder.getId() + " open";

        System.out.println();
        System.out.println("========== MENU (" + orderInfo + ") ==========");
        System.out.println(" 1. List products");
        System.out.println(" 2. Start new order");
        System.out.println(" 3. Add item to order");
        System.out.println(" 4. Remove item from order");
        System.out.println(" 5. View current order");
        System.out.println(" 6. Apply discount");
        System.out.println(" 7. Set tax policy");
        System.out.println(" 8. Checkout");
        System.out.println(" 9. Cancel order");
        System.out.println("10. Restock product");
        System.out.println(" 0. Exit");
    }

    private void listProducts() {
        System.out.println("\n--- Products ---");
        for (Product product : inventory.listAll()) {
            System.out.println("  " + product);
        }
    }

    private void startOrder() {
        if (currentOrder != null) {
            System.out.println("Order #" + currentOrder.getId()
                    + " is still open. Check out or cancel it first.");
            return;
        }
        currentOrder = new Order(nextOrderId++);
        System.out.println("Started order #" + currentOrder.getId());
    }

    private void addItem() {
        Order order = requireOpenOrder();
        listProducts();
        int productId = readInt("Product ID: ");
        Product product = inventory.getById(productId);
        int qty = readInt("Quantity: ");

        order.addItem(product, qty);
        System.out.println("Added " + qty + " x " + product.getName());

        int inOrder = order.quantityOf(productId);
        if (!inventory.isInStock(productId, inOrder)) {
            System.out.println("Warning: " + inOrder + " in order but only " + product.getStockQuantity()
                    + " in stock. Checkout will fail unless you restock.");
        }
    }

    private void removeItem() {
        Order order = requireOpenOrder();
        viewOrder();
        int productId = readInt("Product ID to remove: ");
        order.removeItem(productId);
        System.out.println("Removed product #" + productId);
    }

    private void viewOrder() {
        if (currentOrder == null) {
            System.out.println("No open order.");
            return;
        }

        System.out.println("\n--- Order #" + currentOrder.getId() + " [" + currentOrder.getStatus() + "] ---");
        if (currentOrder.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }

        for (OrderItem item : currentOrder.getItems()) {
            System.out.printf("  #%-3d %-20s x%-3d %12s%n",
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    Money.naira(item.lineTotal()));
        }
        System.out.printf("  %-35s %12s%n", "Subtotal", Money.naira(currentOrder.subTotal()));
        System.out.printf("  %-35s %12s%n",
                "Discount (" + currentOrder.getDiscount().describe() + ")",
                "-" + Money.naira(currentOrder.discountTotal()));
        System.out.printf("  %-35s %12s%n",
                "Tax (" + currentOrder.getTaxPolicy().describe() + ")",
                "+" + Money.naira(currentOrder.taxTotal()));
        System.out.printf("  %-35s %12s%n", "TOTAL", Money.naira(currentOrder.total()));
    }

    private void applyDiscount() {
        Order order = requireOpenOrder();
        System.out.println("1. No discount");
        System.out.println("2. Percentage off");
        System.out.println("3. Fixed amount off");
        System.out.println("4. Buy X get Y free");
        int choice = readInt("Discount type: ");

        Discount discount = switch (choice) {
            case 1 -> new NoDiscount();
            case 2 -> PercentageDiscount.ofPercent(readInt("Percent (1-100): "));
            case 3 -> new FixedAmountDiscount(readLong("Amount in naira: ") * 100);
            case 4 -> new BuyXGetYFree(
                    readInt("Product ID: "),
                    readInt("Buy how many: "),
                    readInt("Get how many free: "));
            default -> throw new IllegalArgumentException("Invalid discount option");
        };

        order.applyDiscount(discount);
        System.out.println("Applied: " + discount.describe());
    }

    private void setTaxPolicy() {
        Order order = requireOpenOrder();
        System.out.println("1. Flat VAT 7.5%");
        System.out.println("2. VAT 7.5% with FOOD exempt");
        System.out.println("3. Tax exempt");
        int choice = readInt("Tax policy: ");

        TaxPolicy policy = switch (choice) {
            case 1 -> new FlatVat();
            case 2 -> new CategoryTax(Set.of(Category.FOOD));
            case 3 -> new TaxExempt();
            default -> throw new IllegalArgumentException("Invalid tax option");
        };

        order.setTaxPolicy(policy);
        System.out.println("Tax policy set: " + policy.describe());
    }

    private void checkout() {
        Order order = requireOpenOrder();
        Receipt receipt = checkoutService.checkout(order);
        System.out.println();
        receiptPrinter.print(receipt);
        currentOrder = null;
    }

    private void cancelOrder() {
        Order order = requireOpenOrder();
        order.cancel();
        System.out.println("Order #" + order.getId() + " cancelled.");
        currentOrder = null;
    }

    private void restock() {
        listProducts();
        int productId = readInt("Product ID: ");
        int qty = readInt("Quantity to add: ");
        inventory.restock(productId, qty);
        System.out.println("Restocked. New stock: " + inventory.getById(productId).getStockQuantity());
    }

    private Order requireOpenOrder() {
        if (currentOrder == null) {
            throw new IllegalStateException("No open order. Start one with option 2.");
        }
        return currentOrder;
    }

    private int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

    private long readLong(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Long.parseLong(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }

}