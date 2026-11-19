package pos.ui.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

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
import pos.ui.AppInfo;
import pos.ui.ReceiptPrinter;

public class PosWindow extends JFrame {
    private static final Color MUTED = new Color(0x6E7781);
    private static final Color OUT_OF_STOCK = new Color(0xCF222E);
    private static final Color LOW_STOCK = new Color(0xBF8700);
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final Inventory inventory;
    private final CheckoutService checkoutService;
    private final ReceiptPrinter receiptPrinter;

    private final DefaultTableModel productModel = readOnlyModel("ID", "Name", "Category", "Price", "Stock");
    private final DefaultTableModel orderModel = readOnlyModel("ID", "Name", "Qty", "Unit price", "Line total");
    private final JTable productTable = new JTable(productModel);
    private final JTable orderTable = new JTable(orderModel);
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<DiscountOption> discountBox = new JComboBox<>(DiscountOption.values());
    private final JComboBox<TaxOption> taxBox = new JComboBox<>(TaxOption.values());

    private final JLabel orderTitle = new JLabel();
    private final JLabel subtotalValue = amountLabel();
    private final JLabel discountName = new JLabel();
    private final JLabel discountValue = amountLabel();
    private final JLabel taxName = new JLabel();
    private final JLabel taxValue = amountLabel();
    private final JLabel totalValue = amountLabel();
    private final JButton removeButton = new JButton("Remove selected");
    private final JButton checkoutButton = new JButton("Checkout");

    private Order currentOrder;
    private int nextOrderId = 1;
    private boolean resettingTaxBox;

    public PosWindow(Inventory inventory, CheckoutService checkoutService, ReceiptPrinter receiptPrinter) {
        super(AppInfo.NAME);
        if (inventory == null || checkoutService == null || receiptPrinter == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.inventory = inventory;
        this.checkoutService = checkoutService;
        this.receiptPrinter = receiptPrinter;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(buildContent());
        startNewOrder();

        setMinimumSize(new Dimension(1000, 620));
        pack();
        setLocationRelativeTo(null);
    }

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildProductsPanel(), buildOrderPanel());
        split.setResizeWeight(0.5);
        split.setBorder(null);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHeader() {
        JLabel title = new JLabel(AppInfo.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        orderTitle.setForeground(MUTED);
        orderTitle.setFont(orderTitle.getFont().deriveFont(14f));

        JPanel header = new JPanel(new BorderLayout());
        header.add(title, BorderLayout.WEST);
        header.add(orderTitle, BorderLayout.EAST);
        return header;
    }

    private JComponent buildProductsPanel() {
        configureTable(productTable);
        setColumnWidths(productTable, 40, 180, 100, 100, 60);
        alignRight(productTable, 3);
        productTable.getColumnModel().getColumn(4).setCellRenderer(new StockRenderer());
        productTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && productTable.getSelectedRow() >= 0) {
                    safely(PosWindow.this::addSelectedProduct);
                }
            }
        });

        JButton addButton = new JButton("Add to order");
        addButton.addActionListener(e -> safely(this::addSelectedProduct));
        JButton restockButton = new JButton("Restock...");
        restockButton.addActionListener(e -> safely(this::restockSelectedProduct));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(new JLabel("Qty"));
        actions.add(qtySpinner);
        actions.add(addButton);
        actions.add(Box.createHorizontalStrut(12));
        actions.add(restockButton);

        JPanel panel = section("Products  (double-click to add)");
        panel.add(new JScrollPane(productTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildOrderPanel() {
        configureTable(orderTable);
        setColumnWidths(orderTable, 40, 170, 50, 100, 110);
        alignRight(orderTable, 2, 3, 4);

        removeButton.addActionListener(e -> safely(this::removeSelectedItem));
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        removeRow.add(removeButton);

        JPanel items = section("Current order");
        items.add(new JScrollPane(orderTable), BorderLayout.CENTER);
        items.add(removeRow, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.add(items, BorderLayout.CENTER);
        right.add(buildPaymentPanel(), BorderLayout.SOUTH);
        return right;
    }

    private JComponent buildPaymentPanel() {
        JButton applyDiscount = new JButton("Apply");
        applyDiscount.addActionListener(e -> safely(this::applySelectedDiscount));
        taxBox.addActionListener(e -> {
            if (!resettingTaxBox) {
                safely(() -> currentOrder.setTaxPolicy(selectedTaxOption().create()));
            }
        });

        JPanel pricing = new JPanel(new GridBagLayout());
        addPricingRow(pricing, 0, "Discount", discountBox, applyDiscount);
        addPricingRow(pricing, 1, "Tax", taxBox, null);

        JPanel totals = new JPanel(new GridBagLayout());
        addTotalRow(totals, 0, new JLabel("Subtotal"), subtotalValue);
        addTotalRow(totals, 1, discountName, discountValue);
        addTotalRow(totals, 2, taxName, taxValue);
        GridBagConstraints sep = new GridBagConstraints();
        sep.gridy = 3;
        sep.gridwidth = 2;
        sep.fill = GridBagConstraints.HORIZONTAL;
        sep.insets = new Insets(6, 0, 6, 0);
        totals.add(new JSeparator(), sep);
        JLabel totalName = new JLabel("TOTAL");
        Font big = totalName.getFont().deriveFont(Font.BOLD, 18f);
        totalName.setFont(big);
        totalValue.setFont(big);
        addTotalRow(totals, 4, totalName, totalValue);

        JButton cancelButton = new JButton("Cancel order");
        cancelButton.addActionListener(e -> safely(this::cancelOrder));
        checkoutButton.setFont(checkoutButton.getFont().deriveFont(Font.BOLD));
        checkoutButton.addActionListener(e -> safely(this::checkout));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(checkoutButton);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (JComponent part : new JComponent[] {pricing, totals, buttons}) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(part);
            body.add(Box.createVerticalStrut(10));
        }

        JPanel panel = section("Payment");
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private void startNewOrder() {
        currentOrder = new Order(nextOrderId++);
        discountBox.setSelectedItem(DiscountOption.NONE);
        resettingTaxBox = true;
        taxBox.setSelectedItem(TaxOption.FLAT_VAT);
        resettingTaxBox = false;
        refresh();
    }

    private void addSelectedProduct() {
        Product product = selectedProduct();
        int qty = spinnerValue();
        int alreadyInOrder = currentOrder.quantityOf(product.getId());

        if (!inventory.isInStock(product.getId(), alreadyInOrder + qty)) {
            throw new IllegalStateException(String.format(
                    "Only %d %s in stock (%d already in this order).",
                    product.getStockQuantity(), product.getName(), alreadyInOrder));
        }
        currentOrder.addItem(product, qty);
        qtySpinner.setValue(1);
    }

    private void removeSelectedItem() {
        int row = orderTable.getSelectedRow();
        if (row < 0) {
            throw new IllegalStateException("Select an item in the order first.");
        }
        currentOrder.removeItem((Integer) orderModel.getValueAt(orderTable.convertRowIndexToModel(row), 0));
    }

    private void restockSelectedProduct() {
        Product product = selectedProduct();
        int qty = askInt("Add how many units of " + product.getName() + "?", "10");
        inventory.restock(product.getId(), qty);
    }

    private void applySelectedDiscount() {
        DiscountOption option = (DiscountOption) discountBox.getSelectedItem();
        currentOrder.applyDiscount(promptForDiscount(option));
    }

    private Discount promptForDiscount(DiscountOption option) {
        return switch (option) {
            case NONE -> new NoDiscount();
            case PERCENTAGE -> PercentageDiscount.ofPercent(askInt("Percent off (1-100):", "10"));
            case FIXED -> new FixedAmountDiscount(askKobo("Amount off in naira (e.g. 500 or 499.99):"));
            case BUY_X_GET_Y -> new BuyXGetYFree(
                    askInt("Product ID the offer applies to:", defaultPromoProductId()),
                    askInt("Buy how many:", "2"),
                    askInt("Get how many free:", "1"));
        };
    }

    private void checkout() {
        Receipt receipt = checkoutService.checkout(currentOrder);
        showReceipt(receipt);
        startNewOrder();
    }

    private void cancelOrder() {
        if (!currentOrder.isEmpty()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Cancel order #" + currentOrder.getId() + "? Its items will be discarded.",
                    "Cancel order", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        currentOrder.cancel();
        startNewOrder();
    }

    private void showReceipt(Receipt receipt) {
        String text = receiptPrinter.format(receipt);
        JTextArea area = new JTextArea(text);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setEditable(false);
        area.setMargin(new Insets(8, 8, 8, 8));
        area.setRows(Math.min(text.split("\\R").length, 30));
        area.setColumns(52);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Receipt - order #" + receipt.getOrderId(), JOptionPane.PLAIN_MESSAGE);
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (InputCancelled ignored) {
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Can't do that", JOptionPane.WARNING_MESSAGE);
        } finally {
            refresh();
        }
    }

    private void refresh() {
        Integer selectedProductId = selectedId(productTable, productModel);
        Integer selectedItemId = selectedId(orderTable, orderModel);

        productModel.setRowCount(0);
        for (Product p : inventory.listAll()) {
            productModel.addRow(new Object[] {
                    p.getId(), p.getName(), p.getCategory(), Money.naira(p.getPrice()), p.getStockQuantity()});
        }

        orderModel.setRowCount(0);
        int units = 0;
        for (OrderItem item : currentOrder.getItems()) {
            orderModel.addRow(new Object[] {
                    item.getProduct().getId(), item.getProduct().getName(), item.getQuantity(),
                    Money.naira(item.getUnitPrice()), Money.naira(item.lineTotal())});
            units += item.getQuantity();
        }

        reselect(productTable, productModel, selectedProductId);
        reselect(orderTable, orderModel, selectedItemId);

        orderTitle.setText("Order #" + currentOrder.getId() + "  ·  " + units + (units == 1 ? " item" : " items"));
        subtotalValue.setText(Money.naira(currentOrder.subTotal()));
        discountName.setText("Discount (" + currentOrder.getDiscount().describe() + ")");
        discountValue.setText("-" + Money.naira(currentOrder.discountTotal()));
        taxName.setText("Tax (" + currentOrder.getTaxPolicy().describe() + ")");
        taxValue.setText("+" + Money.naira(currentOrder.taxTotal()));
        totalValue.setText(Money.naira(currentOrder.total()));

        boolean hasItems = !currentOrder.isEmpty();
        checkoutButton.setEnabled(hasItems);
        removeButton.setEnabled(hasItems);
    }

    private Product selectedProduct() {
        Integer id = selectedId(productTable, productModel);
        if (id == null) {
            throw new IllegalStateException("Select a product first.");
        }
        return inventory.getById(id);
    }

    private String defaultPromoProductId() {
        Integer id = selectedId(orderTable, orderModel);
        if (id == null) {
            id = selectedId(productTable, productModel);
        }
        return id == null ? "" : id.toString();
    }

    private TaxOption selectedTaxOption() {
        return (TaxOption) taxBox.getSelectedItem();
    }

    private int spinnerValue() {
        try {
            qtySpinner.commitEdit();
        } catch (ParseException e) {
            throw new IllegalArgumentException("Quantity must be a whole number between 1 and 999.");
        }
        return (Integer) qtySpinner.getValue();
    }

    private String ask(String prompt, String initial) {
        String answer = (String) JOptionPane.showInputDialog(
                this, prompt, getTitle(), JOptionPane.QUESTION_MESSAGE, null, null, initial);
        if (answer == null) {
            throw new InputCancelled();
        }
        return answer.trim();
    }

    private int askInt(String prompt, String initial) {
        String answer = ask(prompt, initial);
        try {
            return Integer.parseInt(answer);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + answer + "\" is not a whole number.");
        }
    }

    private long askKobo(String prompt) {
        String answer = ask(prompt, "");
        try {
            return new BigDecimal(answer).movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("\"" + answer + "\" is not a valid naira amount.");
        }
    }

    private static Integer selectedId(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        return row < 0 ? null : (Integer) model.getValueAt(table.convertRowIndexToModel(row), 0);
    }

    private static void reselect(JTable table, DefaultTableModel model, Integer id) {
        if (id == null) {
            return;
        }
        for (int row = 0; row < model.getRowCount(); row++) {
            if (id.equals(model.getValueAt(row, 0))) {
                int viewRow = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static void configureTable(JTable table) {
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
    }

    private static void setColumnWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static void alignRight(JTable table, int... columns) {
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(right);
        }
    }

    private static JPanel section(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title), new EmptyBorder(6, 8, 8, 8)));
        return panel;
    }

    private static JLabel amountLabel() {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    private static void addPricingRow(JPanel panel, int row, String label, JComponent field, JComponent extra) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(2, 0, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), c);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        panel.add(field, c);
        if (extra != null) {
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            c.insets = new Insets(2, 0, 2, 0);
            panel.add(extra, c);
        }
    }

    private static void addTotalRow(JPanel panel, int row, JLabel name, JLabel value) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(2, 0, 2, 0);
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        panel.add(name, c);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0;
        panel.add(value, c);
    }

    private static class StockRenderer extends DefaultTableCellRenderer {
        StockRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int stock = (Integer) value;
            if (isSelected) {
                setForeground(table.getSelectionForeground());
            } else if (stock == 0) {
                setForeground(OUT_OF_STOCK);
            } else if (stock <= LOW_STOCK_THRESHOLD) {
                setForeground(LOW_STOCK);
            } else {
                setForeground(table.getForeground());
            }
            setText(stock == 0 ? "Sold out" : String.valueOf(stock));
            return this;
        }
    }

    private static class InputCancelled extends RuntimeException {}

    private enum DiscountOption {
        NONE("No discount"),
        PERCENTAGE("Percentage off"),
        FIXED("Fixed amount off"),
        BUY_X_GET_Y("Buy X get Y free");

        private final String label;

        DiscountOption(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum TaxOption {
        FLAT_VAT(FlatVat::new),
        FOOD_EXEMPT(() -> new CategoryTax(Set.of(Category.FOOD))),
        EXEMPT(TaxExempt::new);

        private final Supplier<TaxPolicy> factory;

        TaxOption(Supplier<TaxPolicy> factory) {
            this.factory = factory;
        }

        TaxPolicy create() {
            return factory.get();
        }

        @Override
        public String toString() {
            return create().describe();
        }
    }
}
