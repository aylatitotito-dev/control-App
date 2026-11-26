package com.appcontrol.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.appcontrol.ui.ThemeManagerMa.AccentJtMa;
import com.appcontrol.ui.ThemeManagerMa.ModeJtMa;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * "Aspecto" de la configuración, como en Zoom: tres miniaturas para elegir el modo de color (Claro, Oscuro o el del
 * sistema) y círculos para elegir el color del tema. Los cambios se aplican y se recuerdan al instante.
 */
final class AppearancePanelJt extends JPanel {

    private final ThemeManagerMa themeJt;

    AppearancePanelJt(ThemeManagerMa themeJt) {
        this.themeJt = themeJt;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(left(UiKitJtMa.title("Aspecto")));
        add(Box.createVerticalStrut(12));
        add(left(modeCardJt()));
        add(Box.createVerticalStrut(12));
        add(left(accentCardJt()));
        themeJt.addListenerJt(this::repaint);
    }

    private static JComponent left(JComponent compJt) {
        compJt.setAlignmentX(LEFT_ALIGNMENT);
        return compJt;
    }

    private JPanel cardJt(String titleJt, String subtitleJt) {
        JPanel cardJt = UiKitJtMa.roundedPanel(18);
        cardJt.setLayout(new BoxLayout(cardJt, BoxLayout.Y_AXIS));
        JLabel headJt = new JLabel(titleJt);
        headJt.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        headJt.setAlignmentX(LEFT_ALIGNMENT);
        cardJt.add(headJt);
        if (subtitleJt != null) {
            cardJt.add(Box.createVerticalStrut(2));
            cardJt.add(left(UiKitJtMa.subtitle(subtitleJt)));
        }
        cardJt.add(Box.createVerticalStrut(12));
        return cardJt;
    }

    // ------------------------------------------------------------------ modo de color

    private JPanel modeCardJt() {
        JPanel cardJt = cardJt("Modo de color", null);
        JPanel rowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        rowJt.setOpaque(false);
        rowJt.add(new ModeTileJtMa(ModeJtMa.LIGHT, "Claro"));
        rowJt.add(new ModeTileJtMa(ModeJtMa.DARK, "Oscuro"));
        rowJt.add(new ModeTileJtMa(ModeJtMa.AUTO, "Configuración del sistema"));
        cardJt.add(left(rowJt));
        cardJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, cardJt.getPreferredSize().height + 8));
        return cardJt;
    }

    /** Una miniatura de ventana (claro, oscuro o mitad y mitad) con su nombre; la elegida lleva borde de acento. */
    private final class ModeTileJtMa extends JComponent {
        private final ModeJtMa modeJtMa;
        private final String labelJtMa;

        ModeTileJtMa(ModeJtMa modeJtMa, String labelJtMa) {
            this.modeJtMa = modeJtMa;
            this.labelJtMa = labelJtMa;
            setPreferredSize(new Dimension(210, 150));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evJtMa) {
                    themeJt.setModeJt(modeJtMa);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics gJtMa) {
            Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
            g2JtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int previewHJtMa = getHeight() - 28;
            boolean selectedJtMa = themeJt.mode() == modeJtMa;
            // borde
            g2JtMa.setStroke(new BasicStroke(selectedJtMa ? 3f : 1f));
            g2JtMa.setColor(selectedJtMa ? ThemeManagerMa.accentColorJt() : ThemeManagerMa.borderColorJt());
            g2JtMa.drawRoundRect(2, 2, getWidth() - 5, previewHJtMa - 3, 18, 18);
            // contenido recortado a las esquinas
            RoundRectangle2D clipJtMa = new RoundRectangle2D.Float(5, 5, getWidth() - 10, previewHJtMa - 9, 14, 14);
            g2JtMa.setClip(clipJtMa);
            if (modeJtMa == ModeJtMa.AUTO) {
                drawWindowJtMa(g2JtMa, false);
                g2JtMa.setClip(clipJtMa);
                g2JtMa.clipRect(getWidth() / 2, 0, getWidth(), previewHJtMa);
                drawWindowJtMa(g2JtMa, true);
            } else {
                drawWindowJtMa(g2JtMa, modeJtMa == ModeJtMa.DARK);
            }
            g2JtMa.setClip(null);
            g2JtMa.setColor(getForeground() == null ? Color.GRAY : UiKitJtMaColors.textJt());
            g2JtMa.setFont(getFont().deriveFont(selectedJtMa ? Font.BOLD : Font.PLAIN, 13f));
            java.awt.FontMetrics fmJtMa = g2JtMa.getFontMetrics();
            g2JtMa.drawString(labelJtMa, (getWidth() - fmJtMa.stringWidth(labelJtMa)) / 2, getHeight() - 8);
            g2JtMa.dispose();
        }

        /** Una ventana pequeña: fondo, franja lateral, dos líneas de texto y una barrita de acento. */
        private void drawWindowJtMa(Graphics2D g2JtMa, boolean darkJtMa) {
            Color backJtMa = darkJtMa ? new Color(0x1F2229) : Color.WHITE;
            Color railJtMa = darkJtMa ? new Color(0x14161A) : new Color(0xE8EEFA);
            Color lineJtMa = darkJtMa ? new Color(0x4A5160) : new Color(0xC9D3E6);
            g2JtMa.setColor(backJtMa);
            g2JtMa.fillRect(0, 0, getWidth(), getHeight());
            g2JtMa.setColor(railJtMa);
            g2JtMa.fillRect(0, 0, 44, getHeight());
            g2JtMa.setColor(darkJtMa ? new Color(0x2B303A) : Color.WHITE);
            g2JtMa.fillRoundRect(8, 16, 28, 22, 8, 8);
            g2JtMa.setColor(lineJtMa);
            g2JtMa.fillRoundRect(8, 46, 28, 6, 6, 6);
            g2JtMa.fillRoundRect(8, 60, 28, 6, 6, 6);
            g2JtMa.fillRoundRect(60, 22, 90, 8, 6, 6);
            g2JtMa.fillRoundRect(60, 38, 60, 8, 6, 6);
            g2JtMa.setColor(darkJtMa ? ThemeManagerMa.AccentJtMa.AZUL.swatch().brighter() : ThemeManagerMa.accentColorJt());
            g2JtMa.fillRoundRect(60, 64, 46, 12, 8, 8);
            g2JtMa.setColor(darkJtMa ? new Color(0x2B303A) : new Color(0xF1F5FC));
            g2JtMa.fillRoundRect(112, 64, 46, 12, 8, 8);
        }
    }

    // ------------------------------------------------------------------ color del tema

    private JPanel accentCardJt() {
        JPanel cardJt = cardJt("Tema", "Aplica un color de acento a botones, iconos y selecciones.");
        JPanel rowJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 22, 0));
        rowJt.setOpaque(false);
        for (AccentJtMa accentJt : AccentJtMa.values()) {
            rowJt.add(new SwatchJtMa(accentJt));
        }
        cardJt.add(left(rowJt));
        cardJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, cardJt.getPreferredSize().height + 8));
        return cardJt;
    }

    /** Un círculo de color con su nombre debajo; el elegido lleva un aro. */
    private final class SwatchJtMa extends JComponent {
        private final AccentJtMa accentJtMa;

        SwatchJtMa(AccentJtMa accentJtMa) {
            this.accentJtMa = accentJtMa;
            setPreferredSize(new Dimension(76, 96));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(accentJtMa.label());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent evJtMa) {
                    themeJt.setAccentJt(accentJtMa);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics gJtMa) {
            Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
            g2JtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean selectedJtMa = themeJt.accent() == accentJtMa;
            int dJtMa = 56;
            int xJtMa = (getWidth() - dJtMa) / 2;
            if (selectedJtMa) {
                g2JtMa.setStroke(new BasicStroke(3f));
                g2JtMa.setColor(ThemeManagerMa.accentColorJt());
                g2JtMa.drawOval(xJtMa - 5, 0, dJtMa + 10, dJtMa + 10);
            }
            g2JtMa.setColor(accentJtMa.swatch());
            g2JtMa.fillOval(xJtMa, 5, dJtMa, dJtMa);
            g2JtMa.setColor(UiKitJtMaColors.textJt());
            g2JtMa.setFont(getFont().deriveFont(selectedJtMa ? Font.BOLD : Font.PLAIN, 12f));
            java.awt.FontMetrics fmJtMa = g2JtMa.getFontMetrics();
            g2JtMa.drawString(accentJtMa.label(), (getWidth() - fmJtMa.stringWidth(accentJtMa.label())) / 2,
                    dJtMa + 28);
            g2JtMa.dispose();
        }
    }

    /** Color del texto normal del tema actual (para lo que se dibuja a mano). */
    private static final class UiKitJtMaColors {
        private UiKitJtMaColors() {
        }

        static Color textJt() {
            Color colorJt = javax.swing.UIManager.getColor("Label.foreground");
            return colorJt != null ? colorJt : Color.DARK_GRAY;
        }
    }

    /** Borde fino vacío para separar la sección de lo que sigue. */
    @Override
    public void addNotify() {
        super.addNotify();
        setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
    }
}
