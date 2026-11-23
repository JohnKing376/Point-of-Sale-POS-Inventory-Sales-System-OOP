package pos.ui.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/** Colours, fonts and the small set of custom-painted components the POS window is built from. */
public final class Theme {
    static final Color BACKGROUND = new Color(0xF2F2F0);
    static final Color SURFACE = Color.WHITE;
    static final Color SURFACE_ALT = new Color(0xF7F7F5);
    static final Color STRIPE = new Color(0xFAFAF9);
    static final Color BORDER = new Color(0xDADAD6);
    static final Color TEXT = new Color(0x1E1E1E);
    static final Color MUTED = new Color(0x6B6B6B);
    static final Color SUBTLE = new Color(0x9C9C9C);

    static final Color PRIMARY = new Color(0x2F5D8A);
    static final Color PRIMARY_DARK = new Color(0x264C72);
    static final Color PRIMARY_SOFT = new Color(0xE2EAF3);
    static final Color SUCCESS = new Color(0x2E7D32);
    static final Color SUCCESS_DARK = new Color(0x256628);
    static final Color DANGER = new Color(0xB3261E);
    static final Color DANGER_SOFT = new Color(0xF6E1DF);
    static final Color WARNING = new Color(0x9A6700);
    static final Color NEUTRAL_SOFT = new Color(0xEDEDEA);


    private static final String FAMILY = pickFamily("Segoe UI", "Inter", "Helvetica Neue", "Arial");
    private static final String MONO_FAMILY = pickFamily("Consolas", "Cascadia Mono", "Menlo");

    private Theme() {}

    /** Applies the theme font to the standard Swing components (dialogs, combo boxes, spinners...). */
    public static void install() {
        Font base = font(Font.PLAIN, 13f);
        for (Object key : UIManager.getLookAndFeelDefaults().keySet().toArray()) {
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, new FontUIResource(base));
            }
        }
        UIManager.put("OptionPane.messageFont", new FontUIResource(font(Font.PLAIN, 14f)));
        UIManager.put("Table.selectionBackground", PRIMARY_SOFT);
        UIManager.put("Table.selectionForeground", TEXT);
    }

    static Font font(int style, float size) {
        return new Font(FAMILY, style, Math.round(size)).deriveFont(size);
    }

    static Font mono(float size) {
        return new Font(MONO_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size);
    }

    static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    /** A panel that lets its parent's background show through. */
    static JPanel clear(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g2;
    }

    static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static String pickFamily(String... candidates) {
        for (String name : candidates) {
            Font f = new Font(name, Font.PLAIN, 13);
            if (f.getFamily().equals(name) && f.canDisplay('₦')) {
                return name;
            }
        }
        return Font.DIALOG;
    }

    // ------------------------------------------------------------------ cards

    /** A white rounded panel with a soft shadow, a title and an optional subtitle. */
    static final class Card extends JPanel {
        private static final int ARC = 6;
        private final JLabel subtitle;
        private final JPanel heading = clear(new BorderLayout(12, 0));

        Card(String title, String subtitleText) {
            super(new BorderLayout(0, 12));
            setOpaque(false);
            setBorder(new EmptyBorder(14, 16, 16, 16));

            JLabel titleLabel = label(title, Font.BOLD, 15f, TEXT);
            subtitle = label(subtitleText, Font.PLAIN, 12f, MUTED);
            JPanel titles = clear(new BorderLayout(0, 2));
            titles.add(titleLabel, BorderLayout.NORTH);
            if (subtitleText != null) {
                titles.add(subtitle, BorderLayout.SOUTH);
            }
            heading.add(titles, BorderLayout.CENTER);
            add(heading, BorderLayout.NORTH);
        }

        JLabel subtitle() {
            return subtitle;
        }

        /** Puts a control (usually a button) at the right-hand end of the card's title row. */
        void setHeaderAction(JComponent action) {
            JPanel holder = clear(new java.awt.GridBagLayout());
            holder.add(action);
            heading.add(holder, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(SURFACE);
            g2.fillRoundRect(0, 0, w, h, ARC, ARC);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, w, h, ARC, ARC);
            g2.dispose();
        }
    }

    /** A rounded, filled box used to group the order totals. */
    static final class Well extends JPanel {
        Well(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(new EmptyBorder(12, 16, 12, 16));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(SURFACE_ALT);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- buttons

    enum Variant { PRIMARY, SUCCESS, SECONDARY, DANGER_GHOST }

    /** A flat, rounded button with hover, pressed, focus and disabled states. */
    static final class FlatButton extends JButton {
        private final Variant variant;

        FlatButton(String text, Variant variant) {
            super(text);
            this.variant = variant;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(font(Font.BOLD, 13f));
            setBorder(new EmptyBorder(8, 16, 8, 16));
        }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) {
                return super.getPreferredSize();
            }
            FontMetrics fm = getFontMetrics(getFont());
            Insets in = getInsets();
            int w = fm.stringWidth(getText()) + in.left + in.right;
            int h = Math.max(34, fm.getHeight() + in.top + in.bottom);
            return new Dimension(w, h);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            int h = getHeight();
            boolean enabled = isEnabled();
            boolean hover = enabled && getModel().isRollover();
            boolean pressed = enabled && getModel().isPressed();

            Color fill;
            Color text;
            Color outline = null;
            switch (variant) {
                case PRIMARY -> {
                    fill = pressed || hover ? PRIMARY_DARK : PRIMARY;
                    text = Color.WHITE;
                }
                case SUCCESS -> {
                    fill = pressed || hover ? SUCCESS_DARK : SUCCESS;
                    text = Color.WHITE;
                }
                case DANGER_GHOST -> {
                    fill = pressed ? DANGER_SOFT : hover ? new Color(0xFAEDEC) : new Color(0, 0, 0, 0);
                    text = DANGER;
                }
                default -> {
                    fill = pressed ? NEUTRAL_SOFT : hover ? SURFACE_ALT : SURFACE;
                    text = TEXT;
                    outline = hover ? SUBTLE : new Color(0xC6C6C2);
                }
            }
            if (!enabled) {
                boolean solid = variant == Variant.PRIMARY || variant == Variant.SUCCESS;
                fill = solid ? new Color(0xC6C6C2) : fill;
                text = solid ? Color.WHITE : SUBTLE;
                outline = outline == null ? null : BORDER;
            }

            int arc = 6;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            if (outline != null) {
                g2.setColor(outline);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            if (isFocusOwner() && enabled) {
                g2.setColor(withAlpha(PRIMARY, 110));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }

            g2.setFont(getFont());
            g2.setColor(text);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    // ----------------------------------------------------------------- tables

    private static final Border CELL_PADDING = new EmptyBorder(0, 14, 0, 14);

    static Color rowBackground(boolean selected, int row) {
        return selected ? PRIMARY_SOFT : row % 2 == 0 ? SURFACE : STRIPE;
    }

    /** Applies the shared table look; {@code alignments} gives each column's SwingConstants alignment. */
    static void styleTable(JTable table, int... alignments) {
        table.setRowHeight(38);
        table.setFont(font(Font.PLAIN, 13f));
        table.setForeground(TEXT);
        table.setBackground(SURFACE);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(0xEEF2F6));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(PRIMARY_SOFT);
        table.setSelectionForeground(TEXT);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setBackground(SURFACE_ALT);
        header.setDefaultRenderer(new HeaderRenderer(alignments));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));

        for (int i = 0; i < alignments.length; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new TextCell(alignments[i], Font.PLAIN, null));
        }
    }

    static JScrollPane scroll(JTable table) {
        JScrollPane pane = new JScrollPane(table);
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(SURFACE);
        JPanel corner = new JPanel();
        corner.setBackground(SURFACE_ALT);
        pane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, corner);
        return pane;
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        private final int[] alignments;

        HeaderRenderer(int[] alignments) {
            this.alignments = alignments;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setText(value == null ? "" : value.toString());
            setFont(font(Font.BOLD, 12f));
            setForeground(MUTED);
            setBackground(SURFACE_ALT);
            setOpaque(true);
            setHorizontalAlignment(column < alignments.length ? alignments[column] : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), CELL_PADDING));
            return this;
        }
    }

    /** Plain text cell with padding, zebra striping and a left accent bar on the selected row. */
    static class TextCell extends DefaultTableCellRenderer {
        private final int style;
        private final Color color;

        TextCell(int alignment, int style, Color color) {
            this.style = style;
            this.color = color;
            setHorizontalAlignment(alignment);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            setFont(font(style, 13f));
            setBackground(rowBackground(isSelected, row));
            setForeground(color != null ? color : TEXT);
            setBorder(CELL_PADDING);
            return this;
        }
    }

    /** What a {@link PillCell} draws: a coloured rounded tag, or plain text when {@code background} is null. */
    record Pill(String text, Color foreground, Color background) {}

    /** Draws each value as a small rounded tag. */
    static final class PillCell extends JComponent implements TableCellRenderer {
        private final Function<Object, Pill> mapper;
        private final boolean alignRight;
        private Pill pill;
        private Color rowBackground;

        PillCell(boolean alignRight, Function<Object, Pill> mapper) {
            this.alignRight = alignRight;
            this.mapper = mapper;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            pill = mapper.apply(value);
            rowBackground = rowBackground(isSelected, row);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(rowBackground);
            g2.fillRect(0, 0, getWidth(), getHeight());

            boolean tagged = pill.background() != null;
            g2.setFont(tagged ? font(Font.BOLD, 11.5f) : font(Font.PLAIN, 13f));
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(pill.text());
            int padX = tagged ? 9 : 0;
            int pillWidth = textWidth + padX * 2;
            int pillHeight = fm.getHeight() + 4;
            int x = alignRight ? getWidth() - 14 - pillWidth : 14;
            int y = (getHeight() - pillHeight) / 2;

            if (tagged) {
                g2.setColor(pill.background());
                g2.fillRoundRect(x, y, pillWidth, pillHeight, pillHeight, pillHeight);
            }
            g2.setColor(pill.foreground());
            g2.drawString(pill.text(), x + padX, y + (pillHeight - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
