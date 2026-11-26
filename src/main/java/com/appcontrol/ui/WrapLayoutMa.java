package com.appcontrol.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

import javax.swing.SwingUtilities;

/** FlowLayout que baja a otra linea cuando no hay ancho, calculando bien la altura necesaria. */
final class WrapLayoutMa extends FlowLayout {

    WrapLayoutMa(int alignJt, int hgapJt, int vgapJt) {
        super(alignJt, hgapJt, vgapJt);
    }

    @Override
    public Dimension preferredLayoutSize(Container targetJt) {
        return layoutSizeJt(targetJt, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container targetJt) {
        Dimension minJt = layoutSizeJt(targetJt, false);
        minJt.width -= getHgap() + 1;
        return minJt;
    }

    private Dimension layoutSizeJt(Container targetJt, boolean preferredJt) {
        synchronized (targetJt.getTreeLock()) {
            int widthJt = targetJt.getSize().width;
            Container topJt = targetJt;
            while (topJt.getSize().width == 0 && topJt.getParent() != null) {
                topJt = topJt.getParent();
            }
            widthJt = topJt.getSize().width;
            if (widthJt == 0) {
                widthJt = Integer.MAX_VALUE;
            }
            Insets insetsJt = targetJt.getInsets();
            int horizontalJt = insetsJt.left + insetsJt.right + getHgap() * 2;
            int maxWidthJt = widthJt - horizontalJt;

            Dimension dimJt = new Dimension(0, 0);
            int rowWidthJt = 0;
            int rowHeightJt = 0;
            for (int iJt = 0; iJt < targetJt.getComponentCount(); iJt++) {
                Component mJt = targetJt.getComponent(iJt);
                if (!mJt.isVisible()) {
                    continue;
                }
                Dimension dJt = preferredJt ? mJt.getPreferredSize() : mJt.getMinimumSize();
                if (rowWidthJt + dJt.width > maxWidthJt && rowWidthJt > 0) {
                    dimJt.width = Math.max(dimJt.width, rowWidthJt);
                    dimJt.height += rowHeightJt + getVgap();
                    rowWidthJt = 0;
                    rowHeightJt = 0;
                }
                if (rowWidthJt != 0) {
                    rowWidthJt += getHgap();
                }
                rowWidthJt += dJt.width;
                rowHeightJt = Math.max(rowHeightJt, dJt.height);
            }
            dimJt.width = Math.max(dimJt.width, rowWidthJt);
            dimJt.height += rowHeightJt;
            dimJt.width += horizontalJt;
            dimJt.height += insetsJt.top + insetsJt.bottom + getVgap() * 2;
            Container scrollJt = SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, targetJt);
            if (scrollJt != null && targetJt.isValid()) {
                dimJt.width -= getHgap() + 1;
            }
            return dimJt;
        }
    }
}
