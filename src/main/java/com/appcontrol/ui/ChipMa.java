package com.appcontrol.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

/** Etiqueta redondeada de estado (Permitido, Bloqueado, 83%...). Colores distintos en claro y oscuro. */
final class ChipMa extends JLabel {

    enum KindJtMa { OK, WARN, DANGER, NEUTRAL }

    private final Color bgJt;

    ChipMa(String textJt, KindJtMa kindJt) {
        super(textJt);
        boolean darkJt = FlatLaf.isLafDark();
        Color[] colorsJt = switch (kindJt) {
            case OK -> darkJt ? pair("#1C3A2B", "#5FD08A") : pair("#DDF6E8", "#0E7A3D");
            case WARN -> darkJt ? pair("#433519", "#FCE100") : pair("#FFF4CE", "#805B00");
            case DANGER -> darkJt ? pair("#442726", "#FF99A4") : pair("#FDE7E9", "#C42B1C");
            case NEUTRAL -> darkJt ? pair("#2E333E", "#C5CCD9") : pair("#E8EEFA", "#3E4A63");
        };
        bgJt = colorsJt[0];
        setForeground(colorsJt[1]);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        putClientProperty(FlatClientProperties.STYLE, "font: -1");
    }

    private static Color[] pair(String bgJt, String fgJt) {
        return new Color[] {Color.decode(bgJt), Color.decode(fgJt)};
    }

    @Override
    protected void paintComponent(Graphics gJt) {
        Graphics2D g2Jt = (Graphics2D) gJt.create();
        g2Jt.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2Jt.setColor(bgJt);
        g2Jt.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2Jt.dispose();
        super.paintComponent(gJt);
    }
}
