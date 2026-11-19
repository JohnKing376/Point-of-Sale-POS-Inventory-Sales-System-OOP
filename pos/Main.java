package pos;

import pos.domain.product.Inventory;
import pos.services.CheckoutService;
import pos.ui.ConsoleApp;
import pos.ui.ReceiptPrinter;

public class Main {

    public static void main(String[] args) {
        Inventory inventory = new Inventory();
        SampleData.seed(inventory);

        CheckoutService checkoutService = new CheckoutService(inventory);
        ReceiptPrinter receiptPrinter = new ReceiptPrinter();

        new ConsoleApp(inventory, checkoutService, receiptPrinter).run();
    }

}