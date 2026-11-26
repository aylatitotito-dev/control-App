package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Date;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;

import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.model.BlockRuleJtMa;

/** Cuadro para crear o cambiar la regla de una aplicacion o sitio del equipo de otra persona (bloqueo total, horario o cuota). */
final class RemoteRuleDialogMa {

    private static final String[] DAYS_MA = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private RemoteRuleDialogMa() {
    }

    /** Devuelve la regla elegida, o null si se cancela. */
    static RuleDtoJt showMa(Component parentMa, String kindMa, String keyMa, String displayMa, RuleDtoJt currentMa) {
        JRadioButton alwaysMa = new JRadioButton("Bloqueo total (siempre)");
        JRadioButton scheduleMa = new JRadioButton("Bloquear en un horario");
        JRadioButton quotaMa = new JRadioButton("Límite de tiempo por día");
        ButtonGroup groupMa = new ButtonGroup();
        groupMa.add(alwaysMa);
        groupMa.add(scheduleMa);
        groupMa.add(quotaMa);

        JSpinner fromMa = timeSpinnerMa(currentMa == null ? 22 * 60 : currentMa.startMinute());
        JSpinner toMa = timeSpinnerMa(currentMa == null ? 6 * 60 : currentMa.endMinute());
        JSpinner minutesMa = new JSpinner(new SpinnerNumberModel(currentMa == null || currentMa.quotaMinutes() < 1 ? 60
                : currentMa.quotaMinutes(), 1, 1440, 5));
        JCheckBox[] daysBoxesMa = new JCheckBox[7];
        JPanel daysPanelMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        int maskMa = currentMa == null ? BlockRuleJtMa.ALL_DAYS : currentMa.daysMask();
        for (int dayMa = 0; dayMa < 7; dayMa++) {
            daysBoxesMa[dayMa] = new JCheckBox(DAYS_MA[dayMa], (maskMa & (1 << dayMa)) != 0);
            daysPanelMa.add(daysBoxesMa[dayMa]);
        }

        String modeMa = currentMa == null ? "ALWAYS" : currentMa.mode();
        alwaysMa.setSelected("ALWAYS".equals(modeMa));
        scheduleMa.setSelected("SCHEDULE".equals(modeMa));
        quotaMa.setSelected("QUOTA".equals(modeMa));

        JPanel scheduleRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        scheduleRowMa.add(new JLabel("De"));
        scheduleRowMa.add(fromMa);
        scheduleRowMa.add(new JLabel("a"));
        scheduleRowMa.add(toMa);
        JPanel quotaRowMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quotaRowMa.add(new JLabel("Máximo"));
        quotaRowMa.add(minutesMa);
        quotaRowMa.add(new JLabel("minutos al día"));

        JPanel bodyMa = new JPanel(new GridLayout(0, 1, 0, 4));
        bodyMa.add(new JLabel("<html><b>" + displayMa + "</b> en el equipo de esa persona</html>"));
        bodyMa.add(alwaysMa);
        bodyMa.add(scheduleMa);
        bodyMa.add(scheduleRowMa);
        bodyMa.add(daysPanelMa);
        bodyMa.add(quotaMa);
        bodyMa.add(quotaRowMa);

        JPanel wrapMa = new JPanel(new BorderLayout());
        wrapMa.add(bodyMa, BorderLayout.CENTER);
        while (true) {
            int okMa = JOptionPane.showConfirmDialog(parentMa, wrapMa, "Regla para " + displayMa,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (okMa != JOptionPane.OK_OPTION) {
                return null;
            }
            int daysMaskMa = 0;
            for (int dayMa = 0; dayMa < 7; dayMa++) {
                if (daysBoxesMa[dayMa].isSelected()) {
                    daysMaskMa |= 1 << dayMa;
                }
            }
            int startMa = minutesOfMa(fromMa);
            int endMa = minutesOfMa(toMa);
            if (scheduleMa.isSelected()) {
                if (daysMaskMa == 0) {
                    JOptionPane.showMessageDialog(parentMa, "Elige al menos un día.");
                    continue;
                }
                if (startMa == endMa) {
                    JOptionPane.showMessageDialog(parentMa, "La hora de inicio y la de fin no pueden ser iguales.");
                    continue;
                }
                return new RuleDtoJt(kindMa, keyMa, displayMa, "SCHEDULE", "ACTIVE", daysMaskMa, startMa, endMa, 0);
            }
            if (quotaMa.isSelected()) {
                return new RuleDtoJt(kindMa, keyMa, displayMa, "QUOTA", "ACTIVE", BlockRuleJtMa.ALL_DAYS, 0, 0,
                        ((Number) minutesMa.getValue()).intValue());
            }
            return new RuleDtoJt(kindMa, keyMa, displayMa, "ALWAYS", "ACTIVE", BlockRuleJtMa.ALL_DAYS, 0, 0, 0);
        }
    }

    private static JSpinner timeSpinnerMa(int minutesOfDayMa) {
        java.util.Calendar calMa = java.util.Calendar.getInstance();
        calMa.set(java.util.Calendar.HOUR_OF_DAY, minutesOfDayMa / 60);
        calMa.set(java.util.Calendar.MINUTE, minutesOfDayMa % 60);
        JSpinner spinnerMa = new JSpinner(new SpinnerDateModel(calMa.getTime(), null, null,
                java.util.Calendar.MINUTE));
        spinnerMa.setEditor(new JSpinner.DateEditor(spinnerMa, "HH:mm"));
        return spinnerMa;
    }

    private static int minutesOfMa(JSpinner spinnerMa) {
        java.util.Calendar calMa = java.util.Calendar.getInstance();
        calMa.setTime((Date) spinnerMa.getValue());
        return calMa.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calMa.get(java.util.Calendar.MINUTE);
    }

    /** Texto corto de una regla para mostrar en las tablas. */
    static String textMa(RuleDtoJt ruleMa) {
        if (ruleMa == null) {
            return "—";
        }
        return switch (ruleMa.mode()) {
            case "SCHEDULE" -> String.format("Horario %02d:%02d–%02d:%02d", ruleMa.startMinute() / 60,
                    ruleMa.startMinute() % 60, ruleMa.endMinute() / 60, ruleMa.endMinute() % 60);
            case "QUOTA" -> "Límite " + FmtJt.durationJtMa(ruleMa.quotaMinutes() * 60L) + "/día";
            default -> "Bloqueo total";
        };
    }
}
