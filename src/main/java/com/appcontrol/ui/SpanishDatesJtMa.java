package com.appcontrol.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;

/**
 * Nombres de días y meses en español escritos aquí. El Java que trae el instalador no incluye los datos de idiomas
 * (jdk.localedata): con {@code Locale("es")} saldrían en inglés ("Tue", "Nov") en la aplicación instalada.
 */
final class SpanishDatesJtMa {

    private static final String[] DAYS_JTMA = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final String[] MONTHS_JTMA = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
        "Septiembre", "Octubre", "Noviembre", "Diciembre"};
    private static final DateTimeFormatter DAY_MONTH_JTMA = DateTimeFormatter.ofPattern("dd/MM");

    private SpanishDatesJtMa() {
    }

    static String dayJtMa(DayOfWeek dayJtMa) {
        return DAYS_JTMA[dayJtMa.getValue() - 1];
    }

    static String monthJtMa(Month monthJtMa) {
        return MONTHS_JTMA[monthJtMa.getValue() - 1];
    }

    /** "Martes 24/11". */
    static String dayAndDateJtMa(LocalDate dateJtMa) {
        return dayJtMa(dateJtMa.getDayOfWeek()) + " " + dateJtMa.format(DAY_MONTH_JTMA);
    }

    /** "Mar 24" (para las barras del resumen). */
    static String shortDayJtMa(LocalDate dateJtMa) {
        return dayJtMa(dateJtMa.getDayOfWeek()).substring(0, 3) + " " + String.format("%02d", dateJtMa.getDayOfMonth());
    }
}
