package pos.ui.gui;

import static pos.ui.gui.Theme.clear;
import static pos.ui.gui.Theme.font;
import static pos.ui.gui.Theme.label;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
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
import pos.ui.gui.Theme.Card;
import pos.ui.gui.Theme.FlatButton;
import pos.ui.gui.Theme.Pill;
import pos.ui.gui.Theme.PillCell;
import pos.ui.gui.Theme.TextCell;
import pos.ui.gui.Theme.Variant;
import pos.ui.gui.Theme.Well;

public class PosWindow extends JFrame {
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final Inventory inventory;
    private final CheckoutService checkoutService;
    private final ReceiptPrinter receiptPrinter;

    private final DefaultTableModel productModel = readOnlyModel("ID", "Product", "Category", "Price", "Stock");
    private final DefaultTableModel orderModel = readOnlyModel("ID", "Item", "Qty", "Unit price", "Line total");
    private final JTable productTable = new JTable(productModel);
    private final JTable orderTable = new EmptyStateTable(orderModel,
            "No items yet", "Double-click a product, or select it and press Add to order.");
    private final JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JComboBox<DiscountOption> discountBox = new JComboBox<>(DiscountOption.values());
    private final JComboBox<TaxOption> taxBox = new JComboBox<>(TaxOption.values());

    private final JLabel orderBadge = label("", Font.BOLD, 13f, Theme.TEXT);
    private final JLabel itemsBadge = label("", Font.PLAIN, 13f, Theme.MUTED);
    private Card orderCard;
    private final JLabel subtotalValue = amountLabel();
    private final JLabel discountName = new JLabel();
    private final JLabel discountValue = amountLabel();
    private final JLabel taxName = new JLabel();
    private final JLabel taxValue = amountLabel();
    private final JLabel totalValue = amountLabel();
    private final JButton removeButton = new FlatButton("Remove selected", Variant.DANGER_GHOST);
    private final JButton checkoutButton = new FlatButton("Checkout", Variant.SUCCESS);

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

        setMinimumSize(new Dimension(1000, 640));
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1280, screen.width), Math.min(820, screen.height));
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BACKGROUND);
        root.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildProductsPanel(), buildOrderColumn());
        split.setResizeWeight(0.55);
        split.setOpaque(false);
        split.setDividerSize(16);
        split.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        // Invisible: the gap between the cards is the divider.
                    }
                };
                divider.setBorder(null);
                return divider;
            }
        });
        split.setBorder(null); // after setUI, which would otherwise reinstall the look-and-feel border

        JPanel body = clear(new BorderLayout());
        body.setBorder(new EmptyBorder(16, 16, 16, 16));
        body.add(split, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER), new EmptyBorder(12, 20, 12, 20)));

        JPanel order = clear(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        order.add(orderBadge);
        order.add(itemsBadge);

        header.add(label("Point of Sale", Font.BOLD, 17f, Theme.TEXT), BorderLayout.WEST);
        header.add(order, BorderLayout.EAST);
        return header;
    }

    private JComponent buildProductsPanel() {
        Theme.styleTable(productTable, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.LEFT,
                SwingConstants.RIGHT, SwingConstants.RIGHT);
        setColumnWidths(productTable, 50, 200, 120, 110, 100);
        productTable.getColumnModel().getColumn(0).setCellRenderer(new TextCell(SwingConstants.LEFT, Font.PLAIN, Theme.SUBTLE));
        productTable.getColumnModel().getColumn(1).setCellRenderer(new TextCell(SwingConstants.LEFT, Font.BOLD, null));
        productTable.getColumnModel().getColumn(2).setCellRenderer(new PillCell(false, PosWindow::categoryPill));
        productTable.getColumnModel().getColumn(4).setCellRenderer(new PillCell(true, PosWindow::stockPill));
        productTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && productTable.getSelectedRow() >= 0) {
                    safely(PosWindow.this::addSelectedProduct);
                }
            }
        });

        JButton addButton = new FlatButton("Add to order", Variant.PRIMARY);
        addButton.addActionListener(e -> safely(this::addSelectedProduct));
        JButton restockButton = new FlatButton("Restock…", Variant.SECONDARY);
        restockButton.addActionListener(e -> safely(this::restockSelectedProduct));

        qtySpinner.setFont(font(Font.PLAIN, 13f));
        qtySpinner.setPreferredSize(new Dimension(72, 34));

        JPanel left = clear(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(label("Qty", Font.BOLD, 12.5f, Theme.MUTED));
        left.add(qtySpinner);
        left.add(addButton);
        JPanel actions = clear(new BorderLayout());
        actions.add(left, BorderLayout.WEST);
        actions.add(restockButton, BorderLayout.EAST);

        Card card = new Card("Products", null);
        card.add(Theme.scroll(productTable), BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildOrderColumn() {
        Theme.styleTable(orderTable, SwingConstants.LEFT, SwingConstants.LEFT, SwingConstants.CENTER,
                SwingConstants.RIGHT, SwingConstants.RIGHT);
        setColumnWidths(orderTable, 46, 170, 50, 100, 110);
        orderTable.getColumnModel().getColumn(0).setCellRenderer(new TextCell(SwingConstants.LEFT, Font.PLAIN, Theme.SUBTLE));
        orderTable.getColumnModel().getColumn(1).setCellRenderer(new TextCell(SwingConstants.LEFT, Font.BOLD, null));
        orderTable.getColumnModel().getColumn(3).setCellRenderer(new TextCell(SwingConstants.RIGHT, Font.PLAIN, Theme.MUTED));
        orderTable.getColumnModel().getColumn(4).setCellRenderer(new TextCell(SwingConstants.RIGHT, Font.BOLD, null));

        removeButton.addActionListener(e -> safely(this::removeSelectedItem));

        orderCard = new Card("Current order", null);
        removeButton.setPreferredSize(new Dimension(removeButton.getPreferredSize().width, 20));
        orderCard.setHeaderAction(removeButton);
        JScrollPane orderScroll = Theme.scroll(orderTable);
        orderScroll.setPreferredSize(new Dimension(420, 220));
        orderCard.add(orderScroll, BorderLayout.CENTER);

        JPanel column = clear(new BorderLayout(0, 16));
        column.add(orderCard, BorderLayout.CENTER);
        column.add(buildPaymentPanel(), BorderLayout.SOUTH);
        return column;
    }

    private JComponent buildPaymentPanel() {
        JButton applyDiscount = new FlatButton("Apply", Variant.SECONDARY);
        applyDiscount.addActionListener(e -> safely(this::applySelectedDiscount));
        taxBox.addActionListener(e -> {
            if (!resettingTaxBox) {
                safely(() -> currentOrder.setTaxPolicy(selectedTaxOption().create()));
            }
        });
        for (JComboBox<?> box : java.util.List.of(discountBox, taxBox)) {
            box.setFont(font(Font.PLAIN, 13f));
            box.setPreferredSize(new Dimension(box.getPreferredSize().width, 34));
        }

        JPanel discountField = clear(new BorderLayout(8, 0));
        discountField.add(discountBox, BorderLayout.CENTER);
        discountField.add(applyDiscount, BorderLayout.EAST);
        JPanel pricing = clear(new GridLayout(1, 2, 14, 0));
        pricing.add(field("Discount", discountField));
        pricing.add(field("Tax", taxBox));

        for (JLabel name : new JLabel[] {discountName, taxName}) {
            name.setFont(font(Font.PLAIN, 13f));
            name.setForeground(Theme.MUTED);
        }
        Well totals = new Well(new GridBagLayout());
        addTotalRow(totals, 0, label("Subtotal", Font.PLAIN, 13f, Theme.MUTED), subtotalValue, 0);
        addTotalRow(totals, 1, discountName, discountValue, 0);
        addTotalRow(totals, 2, taxName, taxValue, 0);
        JComponent rule = new JPanel();
        rule.setBackground(Theme.BORDER);
        rule.setPreferredSize(new Dimension(1, 1));
        GridBagConstraints sep = new GridBagConstraints();
        sep.gridy = 3;
        sep.gridwidth = 2;
        sep.fill = GridBagConstraints.HORIZONTAL;
        sep.insets = new Insets(8, 0, 6, 0);
        totals.add(rule, sep);
        JLabel totalName = label("Total due", Font.BOLD, 15f, Theme.TEXT);
        totalValue.setFont(font(Font.BOLD, 22f));
        addTotalRow(totals, 4, totalName, totalValue, 0);

        JButton cancelButton = new FlatButton("Cancel order", Variant.SECONDARY);
        cancelButton.addActionListener(e -> safely(this::cancelOrder));
        checkoutButton.setFont(font(Font.BOLD, 15f));
        checkoutButton.setPreferredSize(new Dimension(220, 44));
        checkoutButton.addActionListener(e -> safely(this::checkout));
        cancelButton.setPreferredSize(new Dimension(cancelButton.getPreferredSize().width, 44));

        JPanel buttons = clear(new GridBagLayout());
        GridBagConstraints b = new GridBagConstraints();
        b.fill = GridBagConstraints.BOTH;
        b.insets = new Insets(0, 0, 0, 10);
        buttons.add(cancelButton, b);
        b.weightx = 1;
        b.insets = new Insets(0, 0, 0, 0);
        buttons.add(checkoutButton, b);

        JPanel body = clear(new BorderLayout(0, 12));
        body.add(pricing, BorderLayout.NORTH);
        body.add(totals, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);

        Card card = new Card("Payment", null);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    // ----------------------------------------------------------------- actions

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
        ReceiptDialog.show(this, receipt, receiptPrinter.format(receipt));
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

        orderBadge.setText("Order #" + currentOrder.getId());
        itemsBadge.setText("·  " + units + (units == 1 ? " item" : " items"));

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

    // ----------------------------------------------------------------- helpers

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

    private static Pill categoryPill(Object value) {
        Category category = (Category) value;
        String name = category.name().charAt(0) + category.name().substring(1).toLowerCase(Locale.ROOT);
        return new Pill(name, Theme.MUTED, null);
    }

    private static Pill stockPill(Object value) {
        int stock = (Integer) value;
        if (stock == 0) {
            return new Pill("Sold out", Theme.DANGER, null);
        }
        if (stock <= LOW_STOCK_THRESHOLD) {
            return new Pill(String.valueOf(stock), Theme.WARNING, null);
        }
        return new Pill(String.valueOf(stock), Theme.TEXT, null);
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

    private static void setColumnWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static JLabel amountLabel() {
        JLabel label = new JLabel();
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setFont(font(Font.PLAIN, 13f));
        label.setForeground(Theme.TEXT);
        return label;
    }

    /** A form field: a small caption above the control. */
    private static JComponent field(String caption, JComponent control) {
        JPanel panel = clear(new BorderLayout(0, 6));
        panel.add(label(caption, Font.BOLD, 12f, Theme.MUTED), BorderLayout.NORTH);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    private static void addTotalRow(JPanel panel, int row, JLabel name, JLabel value, int gap) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(2, 0, 2 + gap, 0);
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        panel.add(name, c);
        c.anchor = GridBagConstraints.EAST;
        c.weightx = 0;
        panel.add(value, c);
    }

    // ------------------------------------------------------- small components

    /** A table that shows a friendly message instead of a blank area when it has no rows. */
    private static final class EmptyStateTable extends JTable {
        private final String headline;
        private final String hint;

        EmptyStateTable(DefaultTableModel model, String headline, String hint) {
            super(model);
            this.headline = headline;
            this.hint = hint;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getRowCount() > 0) {
                return;
            }
            Graphics2D g2 = Theme.smooth(g);
            int cx = getWidth() / 2;
            int top = Math.max(8, (getHeight() - 38) / 2);

            g2.setFont(font(Font.BOLD, 13f));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(headline, cx - fm.stringWidth(headline) / 2, top + fm.getAscent());
            g2.setFont(font(Font.PLAIN, 12.5f));
            FontMetrics small = g2.getFontMetrics();
            g2.setColor(Theme.MUTED);
            g2.drawString(hint, cx - small.stringWidth(hint) / 2, top + fm.getHeight() + 2 + small.getAscent());
            g2.dispose();
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
