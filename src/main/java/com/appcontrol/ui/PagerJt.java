package com.appcontrol.ui;

import java.awt.FlowLayout;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Pie de paginas ("« ‹ Anterior   Página 2 de 7   Siguiente › »"). Se muestran de 10 en 10 para que la pantalla
 * no dibuje cientos de tarjetas (con sus iconos) de golpe. Se oculta solo si todo cabe en una pagina.
 */
final class PagerJt extends JPanel {

    static final int PAGE_SIZE_JT = 10;

    private final JButton firstJt = button("«", "Primera página");
    private final JButton prevJt = button("‹  Anterior", "Página anterior");
    private final JButton nextJt = button("Siguiente  ›", "Página siguiente");
    private final JButton lastJt = button("»", "Última página");
    private final JLabel infoJt = new JLabel(" ");
    private final IntConsumer onPageJt;
    private int pageJt;
    private int pagesJt = 1;

    /** onPageJt recibe el numero de pagina elegido (empieza en 0). */
    PagerJt(IntConsumer onPageJt) {
        super(new FlowLayout(FlowLayout.CENTER, 8, 0));
        this.onPageJt = onPageJt;
        setOpaque(false);
        add(firstJt);
        add(prevJt);
        add(infoJt);
        add(nextJt);
        add(lastJt);
        firstJt.addActionListener(evJt -> go(0));
        prevJt.addActionListener(evJt -> go(pageJt - 1));
        nextJt.addActionListener(evJt -> go(pageJt + 1));
        lastJt.addActionListener(evJt -> go(pagesJt - 1));
        setVisible(false);
    }

    private static JButton button(String textJt, String tipJt) {
        JButton btnJt = new JButton(textJt);
        btnJt.setToolTipText(tipJt);
        btnJt.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnJt.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 5,12,5,12");
        btnJt.setFocusable(false);
        return btnJt;
    }

    private void go(int newPageJt) {
        if (newPageJt >= 0 && newPageJt < pagesJt && newPageJt != pageJt) {
            onPageJt.accept(newPageJt);
        }
    }

    /** Cuantas paginas hacen falta para "totalJt" elementos (minimo 1). */
    static int pagesFor(int totalJt) {
        return Math.max(1, (totalJt + PAGE_SIZE_JT - 1) / PAGE_SIZE_JT);
    }

    /** Deja la pagina dentro de lo valido (por ejemplo, si al filtrar quedan menos paginas). */
    static int clampJt(int pageJt, int totalJt) {
        return Math.max(0, Math.min(pageJt, pagesFor(totalJt) - 1));
    }

    /** Los elementos de la pagina "pageJt" (empieza en 0). */
    static <T> List<T> sliceJt(List<T> allJt, int pageJt) {
        int fromJt = Math.min(allJt.size(), pageJt * PAGE_SIZE_JT);
        int toJt = Math.min(allJt.size(), fromJt + PAGE_SIZE_JT);
        return allJt.subList(fromJt, toJt);
    }

    /** Actualiza botones y texto para la pagina actual y el total de elementos. */
    void showJt(int currentPageJt, int totalJt) {
        pagesJt = pagesFor(totalJt);
        pageJt = clampJt(currentPageJt, totalJt);
        int fromJt = totalJt == 0 ? 0 : pageJt * PAGE_SIZE_JT + 1;
        int toJt = Math.min(totalJt, (pageJt + 1) * PAGE_SIZE_JT);
        infoJt.setText("Página " + (pageJt + 1) + " de " + pagesJt + "   ·   mostrando " + fromJt + "–" + toJt
                + " de " + totalJt);
        firstJt.setEnabled(pageJt > 0);
        prevJt.setEnabled(pageJt > 0);
        nextJt.setEnabled(pageJt < pagesJt - 1);
        lastJt.setEnabled(pageJt < pagesJt - 1);
        setVisible(totalJt > PAGE_SIZE_JT);
    }
}
