package com.appcontrol.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.UIManager;

import com.appcontrol.service.SummaryServiceMa.DayTotalJt;

/** Barras con el tiempo total de cada dia (una por dia). Los colores salen del tema, claro u oscuro. */
final class DayBarsChartJtMa extends JPanel {

    private List<DayTotalJt> daysMa = List.of();

    DayBarsChartJtMa() {
        setOpaque(false);
        setPreferredSize(new Dimension(0, 190));
    }

    void setDataMa(List<DayTotalJt> daysMa) {
        this.daysMa = daysMa;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics gMa) {
        super.paintComponent(gMa);
        if (daysMa.isEmpty()) {
            return;
        }
        Graphics2D g2Ma = (Graphics2D) gMa.create();
        g2Ma.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2Ma.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color textMa = UIManager.getColor("Label.foreground");
        Color dimMa = UIManager.getColor("Label.disabledForeground");
        Color lineMa = UIManager.getColor("Component.borderColor");
        Color accentMa = UIManager.getColor("Component.accentColor");
        if (accentMa == null) {
            accentMa = new Color(0, 103, 192);
        }
        FontMetrics fmMa = g2Ma.getFontMetrics();

        int padMa = 8;
        int topMa = 26;
        int bottomMa = 26;
        int areaHMa = getHeight() - topMa - bottomMa;
        int countMa = daysMa.size();
        int gapMa = 16;
        int barWMa = Math.max(10, (getWidth() - padMa * 2 - gapMa * (countMa - 1)) / countMa);
        long maxMa = Math.max(3600, daysMa.stream().mapToLong(DayTotalJt::seconds).max().orElse(0));
        LocalDate todayMa = LocalDate.now();

        g2Ma.setColor(lineMa);
        g2Ma.drawLine(padMa, topMa + areaHMa, getWidth() - padMa, topMa + areaHMa);
        for (int posMa = 0; posMa < countMa; posMa++) {
            DayTotalJt dayMa = daysMa.get(posMa);
            int xMa = padMa + posMa * (barWMa + gapMa);
            int hMa = (int) Math.round(areaHMa * (dayMa.seconds() / (double) maxMa));
            if (dayMa.seconds() > 0) {
                hMa = Math.max(hMa, 4);
                g2Ma.setColor(dayMa.day().equals(todayMa) ? accentMa : withAlpha(accentMa, 150));
                g2Ma.fillRoundRect(xMa, topMa + areaHMa - hMa, barWMa, hMa, 8, 8);
                g2Ma.setColor(textMa);
                String valueMa = FmtJt.durationJtMa(dayMa.seconds());
                g2Ma.drawString(valueMa, xMa + (barWMa - fmMa.stringWidth(valueMa)) / 2,
                        topMa + areaHMa - hMa - 6);
            } else {
                g2Ma.setColor(dimMa);
                g2Ma.drawString("—", xMa + (barWMa - fmMa.stringWidth("—")) / 2, topMa + areaHMa - 6);
            }
            g2Ma.setColor(dayMa.day().equals(todayMa) ? textMa : dimMa);
            String labelMa = SpanishDatesJtMa.shortDayJtMa(dayMa.day());
            g2Ma.drawString(labelMa, xMa + (barWMa - fmMa.stringWidth(labelMa)) / 2,
                    topMa + areaHMa + fmMa.getAscent() + 8);
        }
        g2Ma.dispose();
    }

    private static Color withAlpha(Color baseMa, int alphaMa) {
        return new Color(baseMa.getRed(), baseMa.getGreen(), baseMa.getBlue(), alphaMa);
    }
}
