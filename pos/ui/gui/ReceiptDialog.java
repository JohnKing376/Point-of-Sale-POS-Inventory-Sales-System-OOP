package pos.ui.gui;

import static pos.ui.gui.Theme.clear;
import static pos.ui.gui.Theme.font;
import static pos.ui.gui.Theme.label;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import pos.domain.Money;
import pos.domain.receipts.Receipt;
import pos.domain.receipts.ReceiptLine;
import pos.ui.AppInfo;
import pos.ui.gui.Theme.FlatButton;
import pos.ui.gui.Theme.Variant;

/** Shows a completed sale as a paper-style receipt. */
final class ReceiptDialog extends JDialog {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final int PAPER_WIDTH = 340;
    private static final int MAX_PAPER_HEIGHT = 560;

    private GridBagConstraints next;

    /** @param plainText the same receipt as text, offered through the Copy button */
    static void show(Frame owner, Receipt receipt, String plainText) {
        ReceiptDialog dialog = new ReceiptDialog(owner, receipt, plainText);
        dialog.setVisible(true);
    }

    private ReceiptDialog(Frame owner, Receipt receipt, String plainText) {
        super(owner, "Receipt - order #" + receipt.getOrderId(), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        JComponent paper = buildPaper(receipt);
        JScrollPane scroll = new JScrollPane(paper);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Dimension size = paper.getPreferredSize();
        if (size.height > MAX_PAPER_HEIGHT) {
            scroll.setPreferredSize(new Dimension(
                    size.width + scroll.getVerticalScrollBar().getPreferredSize().width + 2, MAX_PAPER_HEIGHT));
        }

        JButton copy = new FlatButton("Copy text", Variant.SECONDARY);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(plainText), null);
            copy.setText("Copied");
            Timer reset = new Timer(1500, t -> copy.setText("Copy text"));
            reset.setRepeats(false);
            reset.start();
        });
        JButton done = new FlatButton("Done", Variant.PRIMARY);
        done.setPreferredSize(new Dimension(96, 34));
        done.addActionListener(e -> dispose());

        JPanel buttons = clear(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(copy);
        buttons.add(done);

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.BACKGROUND);
        root.setBorder(new EmptyBorder(20, 24, 16, 24));
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        getRootPane().setDefaultButton(done);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(owner);
    }

    private JComponent buildPaper(Receipt receipt) {
        JPanel paper = new JPanel(new GridBagLayout());
        paper.setBackground(Theme.SURFACE);
        paper.setBorder(new EmptyBorder(22, 24, 22, 24));
        next = new GridBagConstraints();
        next.gridy = 0;

        // Store header
        full(paper, centered(label(AppInfo.STORE_NAME, Font.BOLD, 16f, Theme.TEXT)), 0);
        full(paper, centered(label("Order #" + receipt.getOrderId() + "  ·  "
                + receipt.getIssuedAt().format(DATE_FORMAT), Font.PLAIN, 12f, Theme.MUTED)), 4);
        full(paper, new Rule(), 10);

        // Items: name and line total, with "qty x unit price" underneath
        for (ReceiptLine line : receipt.getLines()) {
            pair(paper, label(line.productName(), Font.BOLD, 13f, Theme.TEXT),
                    amount(Money.naira(line.lineTotal()), Font.PLAIN, 13f, Theme.TEXT), 8);
            full(paper, label(line.quantity() + " × " + Money.naira(line.unitPrice()),
                    Font.PLAIN, 12f, Theme.MUTED), 1);
        }
        full(paper, new Rule(), 10);

        // Totals
        pair(paper, label("Subtotal", Font.PLAIN, 13f, Theme.MUTED),
                amount(Money.naira(receipt.getSubTotal()), Font.PLAIN, 13f, Theme.TEXT), 0);
        if (receipt.getDiscountAmount() > 0) {
            pair(paper, label("Discount (" + receipt.getDiscountDescription() + ")", Font.PLAIN, 13f, Theme.MUTED),
                    amount("-" + Money.naira(receipt.getDiscountAmount()), Font.PLAIN, 13f, Theme.TEXT), 4);
        }
        pair(paper, label("Tax (" + receipt.getTaxDescription() + ")", Font.PLAIN, 13f, Theme.MUTED),
                amount("+" + Money.naira(receipt.getTaxAmount()), Font.PLAIN, 13f, Theme.TEXT), 4);
        full(paper, new Rule(), 10);
        pair(paper, label("Total", Font.BOLD, 15f, Theme.TEXT),
                amount(Money.naira(receipt.getTotal()), Font.BOLD, 18f, Theme.TEXT), 0);
        full(paper, new Rule(), 10);

        // Footer
        full(paper, centered(label("Paid  ·  Thank you for your purchase", Font.PLAIN, 12f, Theme.MUTED)), 0);

        Dimension size = paper.getPreferredSize();
        paper.setPreferredSize(new Dimension(PAPER_WIDTH, size.height));
        return paper;
    }

    /** Adds a component spanning the full width of the receipt. */
    private void full(JPanel paper, JComponent component, int topGap) {
        GridBagConstraints c = (GridBagConstraints) next.clone();
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(topGap, 0, 0, 0);
        paper.add(component, c);
        next.gridy++;
    }

    /** Adds a row with a label on the left and an amount on the right. */
    private void pair(JPanel paper, JComponent left, JComponent right, int topGap) {
        GridBagConstraints c = (GridBagConstraints) next.clone();
        c.gridx = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(topGap, 0, 0, 12);
        paper.add(left, c);
        c.gridx = 1;
        c.weightx = 0;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(topGap, 0, 0, 0);
        paper.add(right, c);
        next.gridy++;
    }

    private static JLabel centered(JLabel label) {
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    private static JLabel amount(String text, int style, float size, Color color) {
        JLabel label = label(text, style, size, color);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        return label;
    }

    /** A dashed divider, like the tear lines on a till receipt. */
    private static final class Rule extends JComponent {
        Rule() {
            setPreferredSize(new Dimension(1, 11));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(0xC8C8C4));
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 3f}, 0f));
            int y = getHeight() / 2;
            g2.drawLine(0, y, getWidth(), y);
            g2.dispose();
        }
    }
}
