package com.appcontrol.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

/**
 * Opción del riel de iconos (como Zoom): el icono arriba, el nombre abajo y, cuando está elegida, una tarjeta redondeada
 * con el icono en el color de acento. Al pasar el mouse se aclara el fondo.
 */
final class RailButtonJtMa extends JToggleButton {

    private final String iconNameJtMa;

    RailButtonJtMa(String labelJtMa, String iconNameJtMa) {
        super(labelJtMa);
        this.iconNameJtMa = iconNameJtMa;
        setHorizontalTextPosition(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.BOTTOM);
        setIconTextGap(4);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(false);
        setOpaque(false);
        putClientProperty("JButton.buttonType", "borderless");
        putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE, "font: -1; margin: 6,0,6,0");
        setPreferredSize(new Dimension(92, 68));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        refreshIconJtMa();
    }

    /** Vuelve a pintar el icono con los colores del tema actual (gris, o el color de acento si está elegido). */
    void refreshIconJtMa() {
        Color colorJtMa = isSelected() ? ThemeManagerMa.accentColorJt() : ThemeManagerMa.mutedColorJt();
        setIcon(IconsJtMa.colored(iconNameJtMa, 24, ThemeManagerMa.hexJt(colorJtMa)));
        setForeground(isSelected() ? ThemeManagerMa.accentColorJt() : ThemeManagerMa.mutedColorJt());
    }

    @Override
    public void setSelected(boolean selectedJtMa) {
        super.setSelected(selectedJtMa);
        refreshIconJtMa();
    }

    @Override
    protected void paintComponent(Graphics gJtMa) {
        Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
        g2JtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (isSelected()) {
            g2JtMa.setColor(ThemeManagerMa.selectedColorJt());
            g2JtMa.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 16, 16);
            g2JtMa.setColor(ThemeManagerMa.borderColorJt());
            g2JtMa.drawRoundRect(4, 2, getWidth() - 9, getHeight() - 5, 16, 16);
        } else if (getModel().isRollover()) {
            g2JtMa.setColor(ThemeManagerMa.hoverColorJt());
            g2JtMa.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 16, 16);
        }
        g2JtMa.dispose();
        super.paintComponent(gJtMa);
    }
}
