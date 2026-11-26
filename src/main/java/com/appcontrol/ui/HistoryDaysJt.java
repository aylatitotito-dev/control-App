package com.appcontrol.ui;

import java.awt.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

/**
 * Las opciones de dia del Historial (Hoy, Ayer, los dias anteriores y "Ultimos 7 dias"), compartidas por la
 * linea de tiempo y el resumen: al elegir un dia en una, la otra muestra el mismo.
 */
final class HistoryDaysJt {

    static final int DAYS_BACK_JT = 7;
    /** Cuántos días atrás guarda el historial (90) y, por tanto, hasta dónde deja llegar el calendario. */
    static final int KEEP_DAYS_JT = 90;
    static final String PICK_LABEL_JT = "📅  Elegir fecha…";
    /** Día elegido en el calendario (compartido por la línea de tiempo y el resumen). */
    private static LocalDate customDayJt;
    private static boolean pickingJt;

    private HistoryDaysJt() {
    }

    /** Periodo elegido: del primer al ultimo dia, ambos incluidos. */
    record RangeJt(LocalDate first, LocalDate last) {
        boolean multiDay() {
            return !first.equals(last);
        }
    }

    static DefaultComboBoxModel<String> newModelJt() {
        DefaultComboBoxModel<String> modelJt = new DefaultComboBoxModel<>();
        LocalDate todayJt = LocalDate.now();
        modelJt.addElement("Hoy");
        modelJt.addElement("Ayer");
        for (int backJt = 2; backJt < DAYS_BACK_JT; backJt++) {
            modelJt.addElement(SpanishDatesJtMa.dayAndDateJtMa(todayJt.minusDays(backJt)));
        }
        modelJt.addElement(customDayJt == null ? PICK_LABEL_JT : labelOfJt(customDayJt));
        modelJt.addElement("Últimos 7 días");
        return modelJt;
    }

    private static String labelOfJt(LocalDate dayJt) {
        return "📅  " + dayJt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Hace que la opción «Elegir fecha…» abra un calendario. Si se elige un día, esa opción pasa a mostrar la fecha y se
     * selecciona; si se cancela, vuelve a «Hoy».
     */
    static void attachPickerJt(JComboBox<String> boxJt, Component parentJt) {
        boxJt.addActionListener(evJt -> {
            int countJt = boxJt.getItemCount();
            if (pickingJt || countJt < 3 || boxJt.getSelectedIndex() != countJt - 2 || !boxJt.isShowing()) {
                return;
            }
            pickingJt = true;
            try {
                LocalDate todayJt = LocalDate.now();
                LocalDate pickedJt = DatePickerMa.showMa(parentJt, customDayJt == null ? todayJt : customDayJt,
                        todayJt.minusDays(KEEP_DAYS_JT), todayJt);
                javax.swing.ComboBoxModel<String> modelJt = boxJt.getModel();
                if (pickedJt == null) {
                    boxJt.setSelectedIndex(0);
                    return;
                }
                customDayJt = pickedJt;
                if (modelJt instanceof DefaultComboBoxModel<String> defaultJt) {
                    defaultJt.removeElementAt(countJt - 2);
                    defaultJt.insertElementAt(labelOfJt(pickedJt), countJt - 2);
                }
                boxJt.setSelectedIndex(countJt - 2);
            } finally {
                pickingJt = false;
            }
        });
    }

    /** Periodo que corresponde a la opcion elegida (la ultima opcion es la semana). */
    static RangeJt rangeFor(int indexJt, int itemCountJt) {
        LocalDate todayJt = LocalDate.now();
        int safeJt = Math.max(0, indexJt);
        if (safeJt >= itemCountJt - 1) {
            return new RangeJt(todayJt.minusDays(DAYS_BACK_JT - 1), todayJt);
        }
        if (safeJt == itemCountJt - 2) {
            LocalDate pickedJt = customDayJt == null ? todayJt : customDayJt;
            return new RangeJt(pickedJt, pickedJt);
        }
        LocalDate dayJt = todayJt.minusDays(safeJt);
        return new RangeJt(dayJt, dayJt);
    }
}
