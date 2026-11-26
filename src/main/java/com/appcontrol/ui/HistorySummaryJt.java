package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import com.appcontrol.service.InstalledAppsServiceJtMa;
import com.appcontrol.service.InstalledAppsServiceJtMa.InstalledAppJt;
import com.appcontrol.service.SummaryServiceMa;
import com.appcontrol.service.SummaryServiceMa.RankedJt;
import com.appcontrol.service.SummaryServiceMa.SummaryJt;
import com.formdev.flatlaf.FlatClientProperties;

/**
 * Resumen por dia (o de los ultimos 7 dias): tiempo total, grafico de barras por dia, y las aplicaciones y
 * sitios que mas tiempo ocuparon. Comparte el selector de dia con la linea de tiempo.
 */
class HistorySummaryJt extends JPanel {

    private static final int TOP_JT = 10;

    private final SummaryServiceMa summaryJt;
    private final InstalledAppsServiceJtMa installedJt;
    private final JComboBox<String> dayBoxJt;
    private final JLabel totalJt = UiKitJtMa.bigValue();
    private final JLabel totalNoteJt = UiKitJtMa.subtitle(" ");
    private final JLabel topAppJt = UiKitJtMa.bigValue();
    private final JLabel topAppNoteJt = UiKitJtMa.subtitle(" ");
    private final JLabel topSiteJt = UiKitJtMa.bigValue();
    private final JLabel topSiteNoteJt = UiKitJtMa.subtitle(" ");
    private final DayBarsChartJtMa chartJt = new DayBarsChartJtMa();
    private final JPanel chartCardJt = UiKitJtMa.roundedPanel(16);
    private final JPanel appRowsJt = new JPanel();
    private final JPanel siteRowsJt = new JPanel();
    private String lastSigJt = "";

    HistorySummaryJt(SummaryServiceMa summaryJt, InstalledAppsServiceJtMa installedJt,
            ComboBoxModel<String> dayModelJt) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        this.summaryJt = summaryJt;
        this.installedJt = installedJt;
        this.dayBoxJt = new JComboBox<>(dayModelJt);
        HistoryDaysJt.attachPickerJt(dayBoxJt, this);
        dayBoxJt.addActionListener(evJt -> refreshJt(true));

        JPanel pickerJt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pickerJt.setOpaque(false);
        pickerJt.add(UiKitJtMa.subtitle("Mostrar:"));
        pickerJt.add(dayBoxJt);

        JPanel cardsJt = new JPanel(new GridLayout(1, 3, 16, 0));
        cardsJt.setOpaque(false);
        cardsJt.add(UiKitJtMa.card("Tiempo activo", totalJt, totalNoteJt));
        cardsJt.add(UiKitJtMa.card("Aplicación con más tiempo", topAppJt, topAppNoteJt));
        cardsJt.add(UiKitJtMa.card("Sitio con más actividad", topSiteJt, topSiteNoteJt));

        chartCardJt.setLayout(new BorderLayout(0, 8));
        chartCardJt.add(UiKitJtMa.sectionTitle("Tiempo por día"), BorderLayout.NORTH);
        chartCardJt.add(chartJt, BorderLayout.CENTER);

        appRowsJt.setOpaque(false);
        appRowsJt.setLayout(new BoxLayout(appRowsJt, BoxLayout.Y_AXIS));
        siteRowsJt.setOpaque(false);
        siteRowsJt.setLayout(new BoxLayout(siteRowsJt, BoxLayout.Y_AXIS));
        JPanel columnsJt = new JPanel(new GridLayout(1, 2, 16, 0));
        columnsJt.setOpaque(false);
        columnsJt.add(listCard("Aplicaciones", appRowsJt));
        columnsJt.add(listCard("Sitios web", siteRowsJt));

        JPanel bodyJt = new JPanel();
        bodyJt.setOpaque(false);
        bodyJt.setLayout(new BoxLayout(bodyJt, BoxLayout.Y_AXIS));
        for (JComponent partJt : new JComponent[] {pickerJt, cardsJt, chartCardJt, columnsJt}) {
            partJt.setAlignmentX(Component.LEFT_ALIGNMENT);
            bodyJt.add(partJt);
            bodyJt.add(Box.createVerticalStrut(14));
        }
        add(UiKitJtMa.vscroll(bodyJt), BorderLayout.CENTER);
        refreshJt(true);
    }

    private static JPanel listCard(String titleJt, JPanel rowsJt) {
        JPanel cardJt = UiKitJtMa.roundedPanel(16);
        cardJt.setLayout(new BorderLayout(0, 8));
        cardJt.add(UiKitJtMa.sectionTitle(titleJt), BorderLayout.NORTH);
        JPanel holderJt = new JPanel(new BorderLayout());
        holderJt.setOpaque(false);
        holderJt.add(rowsJt, BorderLayout.NORTH);
        cardJt.add(holderJt, BorderLayout.CENTER);
        return cardJt;
    }

    /** Vuelve a calcular; solo redibuja si algo cambio. */
    void refreshJt(boolean forceJt) {
        HistoryDaysJt.RangeJt rangeJt = HistoryDaysJt.rangeFor(dayBoxJt.getSelectedIndex(), dayBoxJt.getItemCount());
        SummaryJt dataJt = summaryJt.summarizeJt(rangeJt.first(), rangeJt.last());

        StringBuilder sigJt = new StringBuilder().append(rangeJt).append('|').append(dataJt.totalSeconds());
        dataJt.apps().stream().limit(TOP_JT).forEach(rJt -> sigJt.append(';').append(rJt.key()).append(rJt.seconds())
                .append('/').append(rJt.daysOver()));
        dataJt.sites().stream().limit(TOP_JT).forEach(rJt -> sigJt.append(';').append(rJt.key()).append(rJt.seconds())
                .append('/').append(rJt.hits()));
        sigJt.append('#').append(AppIconsMa.cacheSizeJt());
        if (!forceJt && sigJt.toString().equals(lastSigJt)) {
            return;
        }
        lastSigJt = sigJt.toString();

        Map<String, InstalledAppJt> installedByExeJt = new HashMap<>();
        for (InstalledAppJt appJt : installedJt.cached()) {
            installedByExeJt.put(appJt.exe().toLowerCase(Locale.ROOT), appJt);
        }

        totalJt.setText(dataJt.totalSeconds() == 0 ? "—" : FmtJt.durationJtMa(dataJt.totalSeconds()));
        totalNoteJt.setText(rangeJt.multiDay() ? "en los últimos 7 días" : "ese día");
        if (dataJt.apps().isEmpty()) {
            topAppJt.setText("—");
            topAppNoteJt.setText("Sin datos");
        } else {
            RankedJt firstJt = dataJt.apps().get(0);
            InstalledAppJt knownJt = installedByExeJt.get(firstJt.key());
            topAppJt.setText(knownJt != null ? knownJt.name() : firstJt.display());
            topAppNoteJt.setText(FmtJt.durationJtMa(firstJt.seconds()));
        }
        if (dataJt.sites().isEmpty()) {
            topSiteJt.setText("—");
            topSiteNoteJt.setText("Sin datos");
        } else {
            RankedJt firstJt = dataJt.sites().get(0);
            topSiteJt.setText(firstJt.display());
            topSiteNoteJt.setText(siteDetail(firstJt));
        }

        chartCardJt.setVisible(rangeJt.multiDay());
        chartJt.setDataMa(dataJt.perDay());

        fillApps(dataJt.apps(), installedByExeJt, rangeJt.multiDay());
        fillSites(dataJt.sites());
        revalidate();
        repaint();
    }

    private void fillApps(List<RankedJt> appsJt, Map<String, InstalledAppJt> installedByExeJt, boolean multiJt) {
        appRowsJt.removeAll();
        if (appsJt.isEmpty()) {
            appRowsJt.add(UiKitJtMa.note("No hay uso de aplicaciones registrado en este periodo."));
            return;
        }
        long maxJt = Math.max(1, appsJt.get(0).seconds());
        for (RankedJt rowJt : appsJt.subList(0, Math.min(TOP_JT, appsJt.size()))) {
            InstalledAppJt knownJt = installedByExeJt.get(rowJt.key());
            String nameJt = knownJt != null ? knownJt.name() : rowJt.display();
            String pathJt = knownJt == null ? null : knownJt.path();
            ChipMa chipJt = null;
            if (rowJt.quotaMinutes() > 0 && rowJt.daysOver() > 0) {
                chipJt = new ChipMa(multiJt ? "Superó la cuota " + rowJt.daysOver()
                        + (rowJt.daysOver() == 1 ? " día" : " días") : "Superó la cuota", ChipMa.KindJtMa.DANGER);
            }
            appRowsJt.add(rankRow(sizeJt -> AppIconsMa.cachedOrPlaceholder(pathJt, nameJt, sizeJt), nameJt,
                    rowJt.seconds() / (double) maxJt, FmtJt.durationJtMa(rowJt.seconds()), chipJt));
        }
    }

    private void fillSites(List<RankedJt> sitesJt) {
        siteRowsJt.removeAll();
        if (sitesJt.isEmpty()) {
            siteRowsJt.add(UiKitJtMa.note("No hay sitios registrados en este periodo."));
            return;
        }
        long maxSecJt = sitesJt.get(0).seconds();
        long maxHitsJt = Math.max(1, sitesJt.stream().mapToLong(RankedJt::hits).max().orElse(1));
        for (RankedJt rowJt : sitesJt.subList(0, Math.min(TOP_JT, sitesJt.size()))) {
            double ratioJt = maxSecJt > 0 ? rowJt.seconds() / (double) maxSecJt : rowJt.hits() / (double) maxHitsJt;
            siteRowsJt.add(rankRow(sizeJt -> SiteIconsJt.ofJt(rowJt.key(), sizeJt), rowJt.display(), ratioJt,
                    siteDetail(rowJt), null));
        }
    }

    private static String siteDetail(RankedJt rowJt) {
        String visitsJt = rowJt.hits() + (rowJt.hits() == 1 ? " visita" : " visitas");
        return rowJt.seconds() >= 60 ? FmtJt.durationJtMa(rowJt.seconds()) + " · " + visitsJt : visitsJt;
    }

    /** Una fila: icono, nombre, barra proporcional y el tiempo a la derecha (con una etiqueta si hace falta). */
    private static JPanel rankRow(java.util.function.IntFunction<Icon> iconJt, String nameJt, double ratioJt,
            String rightJt, ChipMa chipJt) {
        JPanel rowJt = new JPanel(new BorderLayout(12, 0));
        rowJt.setOpaque(false);
        rowJt.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0,
                        javax.swing.UIManager.getColor("Component.borderColor")),
                javax.swing.BorderFactory.createEmptyBorder(8, 0, 8, 0)));
        rowJt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel iconLabelJt = new JLabel(iconJt.apply(28));
        iconLabelJt.setPreferredSize(new Dimension(28, 28));
        JPanel iconWrapJt = new JPanel(new java.awt.GridBagLayout());
        iconWrapJt.setOpaque(false);
        iconWrapJt.add(iconLabelJt);

        JProgressBar barJt = new JProgressBar(0, 100);
        barJt.setValue((int) Math.max(2, Math.round(ratioJt * 100)));
        barJt.setPreferredSize(new Dimension(10, 6));
        JPanel centerJt = new JPanel(new BorderLayout(0, 4));
        centerJt.setOpaque(false);
        centerJt.add(new JLabel(nameJt), BorderLayout.NORTH);
        JPanel barWrapJt = new JPanel(new BorderLayout());
        barWrapJt.setOpaque(false);
        barWrapJt.add(barJt, BorderLayout.CENTER);
        centerJt.add(barWrapJt, BorderLayout.CENTER);

        JPanel eastJt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        eastJt.setOpaque(false);
        if (chipJt != null) {
            eastJt.add(chipJt);
        }
        JLabel rightLabelJt = new JLabel(rightJt, JLabel.RIGHT);
        rightLabelJt.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        eastJt.add(rightLabelJt);

        rowJt.add(iconWrapJt, BorderLayout.WEST);
        rowJt.add(centerJt, BorderLayout.CENTER);
        rowJt.add(eastJt, BorderLayout.EAST);
        return rowJt;
    }
}
