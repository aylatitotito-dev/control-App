package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatClientProperties;

/** Calendario para elegir un día (de lunes a domingo, en español). No deja elegir fechas futuras ni más viejas que el límite. */
final class DatePickerMa {


    private DatePickerMa() {
    }

    /** El calendario como panel. onPickMa recibe el día elegido, o null si se cancela. */
    static JPanel panelMa(LocalDate startMa, LocalDate minMa, LocalDate maxMa, Consumer<LocalDate> onPickMa) {
        return new CalendarPanelMa(startMa, minMa, maxMa, onPickMa);
    }

    /** Muestra el calendario y devuelve el día elegido, o null si se cancela. */
    static LocalDate showMa(Component parentMa, LocalDate startMa, LocalDate minMa, LocalDate maxMa) {
        JDialog dialogMa = new JDialog(SwingUtilities.getWindowAncestor(parentMa), "Elegir fecha",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        LocalDate[] chosenMa = {null};
        dialogMa.setContentPane(panelMa(startMa, minMa, maxMa, pickedMa -> {
            chosenMa[0] = pickedMa;
            dialogMa.dispose();
        }));
        dialogMa.pack();
        dialogMa.setLocationRelativeTo(parentMa);
        dialogMa.setVisible(true);
        return chosenMa[0];
    }

    private static final class CalendarPanelMa extends JPanel {
        private final LocalDate minMa;
        private final LocalDate maxMa;
        private final Consumer<LocalDate> onPickMa;
        private final JLabel monthLabelMa = new JLabel("", JLabel.CENTER);
        private final JPanel gridMa = new JPanel(new GridLayout(0, 7, 4, 4));
        private final LocalDate selectedMa;
        private YearMonth shownMa;

        CalendarPanelMa(LocalDate startMa, LocalDate minMa, LocalDate maxMa, Consumer<LocalDate> onPickMa) {
            super(new BorderLayout(0, 10));
            this.minMa = minMa;
            this.maxMa = maxMa;
            this.onPickMa = onPickMa;
            this.selectedMa = startMa;
            this.shownMa = YearMonth.from(startMa);
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

            JButton prevMa = new JButton("‹");
            prevMa.addActionListener(evMa -> {
                shownMa = shownMa.minusMonths(1);
                fillMa();
            });
            JButton nextMa = new JButton("›");
            nextMa.addActionListener(evMa -> {
                shownMa = shownMa.plusMonths(1);
                fillMa();
            });
            monthLabelMa.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
            JPanel headMa = new JPanel(new BorderLayout(8, 0));
            headMa.setOpaque(false);
            headMa.add(prevMa, BorderLayout.WEST);
            headMa.add(monthLabelMa, BorderLayout.CENTER);
            headMa.add(nextMa, BorderLayout.EAST);

            JButton todayMa = new JButton("Hoy");
            todayMa.addActionListener(evMa -> onPickMa.accept(maxMa));
            JButton cancelMa = new JButton("Cancelar");
            cancelMa.addActionListener(evMa -> onPickMa.accept(null));
            JPanel footMa = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            footMa.setOpaque(false);
            footMa.add(todayMa);
            footMa.add(cancelMa);

            gridMa.setOpaque(false);
            add(headMa, BorderLayout.NORTH);
            add(gridMa, BorderLayout.CENTER);
            add(footMa, BorderLayout.SOUTH);
            fillMa();
        }

        private void fillMa() {
            monthLabelMa.setText(SpanishDatesJtMa.monthJtMa(shownMa.getMonth()) + " " + shownMa.getYear());
            gridMa.removeAll();
            for (DayOfWeek dowMa : DayOfWeek.values()) {
                JLabel nameMa = new JLabel(SpanishDatesJtMa.dayJtMa(dowMa).substring(0, 2), JLabel.CENTER);
                nameMa.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
                gridMa.add(nameMa);
            }
            LocalDate firstMa = shownMa.atDay(1);
            for (int blankMa = 0; blankMa < firstMa.getDayOfWeek().getValue() - 1; blankMa++) {
                gridMa.add(new JLabel(" "));
            }
            for (int dayMa = 1; dayMa <= shownMa.lengthOfMonth(); dayMa++) {
                LocalDate dateMa = shownMa.atDay(dayMa);
                JButton btnMa = new JButton(String.valueOf(dayMa));
                btnMa.setPreferredSize(new Dimension(40, 34));
                btnMa.setFocusable(false);
                btnMa.setEnabled(!dateMa.isBefore(minMa) && !dateMa.isAfter(maxMa));
                if (dateMa.equals(selectedMa)) {
                    btnMa.putClientProperty(FlatClientProperties.STYLE,
                            "margin: 2,2,2,2; background: $Component.accentColor; foreground: #FFFFFF; borderWidth: 0");
                } else if (dateMa.equals(maxMa)) {
                    btnMa.putClientProperty(FlatClientProperties.STYLE,
                            "margin: 2,2,2,2; borderColor: $Component.accentColor; focusedBorderColor: $Component.accentColor");
                } else {
                    btnMa.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                            FlatClientProperties.BUTTON_TYPE_BORDERLESS);
                    btnMa.putClientProperty(FlatClientProperties.STYLE, "margin: 2,2,2,2");
                }
                btnMa.addActionListener(evMa -> onPickMa.accept(dateMa));
                gridMa.add(btnMa);
            }
            gridMa.revalidate();
            gridMa.repaint();
            Component parentMa = SwingUtilities.getWindowAncestor(this);
            if (parentMa != null) {
                ((java.awt.Window) parentMa).pack();
            }
        }
    }
}
