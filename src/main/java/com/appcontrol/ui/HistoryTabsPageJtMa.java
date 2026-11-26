package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import com.formdev.flatlaf.FlatClientProperties;

/** Historial con dos vistas: "Línea de tiempo" (todo lo que pasó, con hora) y "Resumen" (totales por día). */
class HistoryTabsPageJtMa extends PageMa {

    private static final String LINE_JTMA = "line";
    private static final String SUMMARY_JTMA = "summary";

    private final HistoryPageMa timelineJtMa;
    private final HistorySummaryJt summaryJtMa;
    private final CardLayout cardsJtMa = new CardLayout();
    private final JPanel bodyJtMa = new JPanel(cardsJtMa);
    private final JToggleButton lineBtnJtMa = tab("Línea de tiempo");
    private final JToggleButton summaryBtnJtMa = tab("Resumen");
    private boolean summaryShownJtMa;

    HistoryTabsPageJtMa(HistoryPageMa timelineJtMa, HistorySummaryJt summaryJtMa) {
        super("Historial");
        this.timelineJtMa = timelineJtMa;
        this.summaryJtMa = summaryJtMa;

        ButtonGroup groupJtMa = new ButtonGroup();
        groupJtMa.add(lineBtnJtMa);
        groupJtMa.add(summaryBtnJtMa);
        lineBtnJtMa.setSelected(true);
        lineBtnJtMa.addActionListener(evJtMa -> showTimelineMa());
        summaryBtnJtMa.addActionListener(evJtMa -> showSummaryMa());

        JPanel tabsJtMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabsJtMa.setOpaque(false);
        tabsJtMa.add(lineBtnJtMa);
        tabsJtMa.add(summaryBtnJtMa);

        JPanel headerJtMa = new JPanel(new BorderLayout(0, 12));
        headerJtMa.setOpaque(false);
        headerJtMa.add(UiKitJtMa.title("Historial"), BorderLayout.NORTH);
        headerJtMa.add(tabsJtMa, BorderLayout.CENTER);

        bodyJtMa.setOpaque(false);
        bodyJtMa.add(timelineJtMa, LINE_JTMA);
        bodyJtMa.add(summaryJtMa, SUMMARY_JTMA);

        add(headerJtMa, BorderLayout.NORTH);
        add(bodyJtMa, BorderLayout.CENTER);
    }

    private static JToggleButton tab(String textJtMa) {
        JToggleButton btnJtMa = new JToggleButton(textJtMa);
        btnJtMa.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnJtMa.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 7,18,7,18");
        btnJtMa.setFocusable(false);
        return btnJtMa;
    }

    void showTimelineMa() {
        summaryShownJtMa = false;
        lineBtnJtMa.setSelected(true);
        cardsJtMa.show(bodyJtMa, LINE_JTMA);
        timelineJtMa.refreshMa();
    }

    void showSummaryMa() {
        summaryShownJtMa = true;
        summaryBtnJtMa.setSelected(true);
        cardsJtMa.show(bodyJtMa, SUMMARY_JTMA);
        summaryJtMa.refreshJt(true);
    }

    @Override
    void refreshMa() {
        if (summaryShownJtMa) {
            summaryJtMa.refreshJt(false);
        } else {
            timelineJtMa.refreshMa();
        }
    }
}
