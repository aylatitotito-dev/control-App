package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JPanel;

/** La superficie de las pantallas: un rectángulo de esquinas redondeadas sobre el marco, como en Zoom. */
final class SurfaceJtMa extends JPanel {

    private static final int ARC_JTMA = 24;

    SurfaceJtMa() {
        super(new BorderLayout());
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics gJtMa) {
        Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
        g2JtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2JtMa.setColor(ThemeManagerMa.surfaceColorJt());
        g2JtMa.fillRoundRect(0, 0, getWidth(), getHeight(), ARC_JTMA, ARC_JTMA);
        g2JtMa.dispose();
    }

    /** Lo de adentro no se sale de las esquinas redondeadas. */
    @Override
    protected void paintChildren(Graphics gJtMa) {
        Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
        g2JtMa.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC_JTMA, ARC_JTMA));
        super.paintChildren(g2JtMa);
        g2JtMa.dispose();
    }

    @Override
    protected void paintBorder(Graphics gJtMa) {
        Graphics2D g2JtMa = (Graphics2D) gJtMa.create();
        g2JtMa.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2JtMa.setColor(ThemeManagerMa.borderColorJt());
        g2JtMa.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC_JTMA, ARC_JTMA);
        g2JtMa.dispose();
    }
}
