package pos;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import pos.domain.product.Inventory;
import pos.services.CheckoutService;
import pos.ui.ReceiptPrinter;
import pos.ui.gui.PosWindow;

public class GuiMain {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
        }

        SwingUtilities.invokeLater(() -> {
            Inventory inventory = new Inventory();
            SampleData.seed(inventory);
            new PosWindow(inventory, new CheckoutService(inventory), new ReceiptPrinter()).setVisible(true);
        });
    }
}
