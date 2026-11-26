package com.appcontrol.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleStateJt;

/** Decide, sin tocar base de datos ni procesos, si una regla esta bloqueando ahora mismo. */
public final class RuleLogicJtMa {

    private static final DateTimeFormatter HOUR_MA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_HOUR_MA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final String[] DAY_LETTERS_MA = {"L", "M", "X", "J", "V", "S", "D"};
    private static final long WARN_SECONDS_MA = 300;

    private RuleLogicJtMa() {
    }

    /**
     * enforcing = la regla se esta aplicando (no esta inactiva ni pausada);
     * blockedNow = ahora mismo debe estar cerrada; quotaWarn = quedan 5 minutos o menos de cuota;
     * remainingSeconds = segundos de cuota que quedan (-1 si no es cuota); label = texto corto para la interfaz.
     */
    public record StatusMa(boolean enforcing, boolean blockedNow, boolean quotaWarn, long remainingSeconds,
            String label) {
    }

    public static StatusMa evaluateMa(BlockRuleJtMa ruleMa, LocalDateTime nowMa, Instant instantMa, long usedSecMa) {
        if (ruleMa.getState() == RuleStateJt.INACTIVE) {
            return new StatusMa(false, false, false, -1, "Desactivada");
        }
        if (ruleMa.getState() == RuleStateJt.PAUSED && ruleMa.getPausedUntil() != null
                && instantMa.isBefore(ruleMa.getPausedUntil())) {
            LocalDateTime untilMa = LocalDateTime.ofInstant(ruleMa.getPausedUntil(), ZoneId.systemDefault());
            String whenMa = untilMa.toLocalDate().equals(nowMa.toLocalDate()) ? untilMa.format(HOUR_MA)
                    : untilMa.format(DAY_HOUR_MA);
            return new StatusMa(false, false, false, -1, "Pausada hasta " + whenMa);
        }
        // activa (o con la pausa ya vencida)
        switch (ruleMa.getMode()) {
            case SCHEDULE -> {
                boolean blockedMa = blockedBySchedule(ruleMa, nowMa);
                String baseMa = daysTextMa(ruleMa.getDaysMask()) + " " + minuteText(ruleMa.getStartMinute()) + "–"
                        + minuteText(ruleMa.getEndMinute());
                return new StatusMa(true, blockedMa, false, -1,
                        baseMa + (blockedMa ? " · bloqueada ahora" : " · permitida ahora"));
            }
            case QUOTA -> {
                long quotaSecMa = ruleMa.getQuotaMinutes() * 60L;
                long remainingMa = Math.max(0, quotaSecMa - usedSecMa);
                boolean blockedMa = remainingMa == 0;
                boolean warnMa = !blockedMa && remainingMa <= WARN_SECONDS_MA;
                String labelMa = blockedMa ? "Cuota agotada (" + minutesText(ruleMa.getQuotaMinutes()) + " al día)"
                        : "Cuota " + minutesText(ruleMa.getQuotaMinutes()) + " · quedan "
                                + minutesText((remainingMa + 59) / 60);
                return new StatusMa(true, blockedMa, warnMa, remainingMa, labelMa);
            }
            default -> {
                return new StatusMa(true, true, false, -1, "Bloqueo total");
            }
        }
    }

    /** Cierto si "ahora" cae dentro de la franja prohibida (admite franjas que cruzan la medianoche). */
    public static boolean blockedBySchedule(BlockRuleJtMa ruleMa, LocalDateTime nowMa) {
        int startMa = ruleMa.getStartMinute();
        int endMa = ruleMa.getEndMinute();
        if (startMa == endMa) {
            return false;
        }
        int minuteMa = nowMa.getHour() * 60 + nowMa.getMinute();
        boolean todayOkMa = dayAllowedMa(ruleMa.getDaysMask(), nowMa.getDayOfWeek());
        if (startMa < endMa) {
            return todayOkMa && minuteMa >= startMa && minuteMa < endMa;
        }
        boolean yesterdayOkMa = dayAllowedMa(ruleMa.getDaysMask(), nowMa.getDayOfWeek().minus(1));
        return (todayOkMa && minuteMa >= startMa) || (yesterdayOkMa && minuteMa < endMa);
    }

    private static boolean dayAllowedMa(int maskMa, DayOfWeek dayMa) {
        return (maskMa & (1 << (dayMa.getValue() - 1))) != 0;
    }

    /** "Todos los días", "L–V", "S–D" o las letras sueltas ("L, X, V"). */
    public static String daysTextMa(int maskMa) {
        if (maskMa == BlockRuleJtMa.ALL_DAYS) {
            return "Todos los días";
        }
        if (maskMa == 0b0011111) {
            return "L–V";
        }
        if (maskMa == 0b1100000) {
            return "S–D";
        }
        StringBuilder sbMa = new StringBuilder();
        for (int posMa = 0; posMa < 7; posMa++) {
            if ((maskMa & (1 << posMa)) != 0) {
                if (sbMa.length() > 0) {
                    sbMa.append(", ");
                }
                sbMa.append(DAY_LETTERS_MA[posMa]);
            }
        }
        return sbMa.length() == 0 ? "Ningún día" : sbMa.toString();
    }

    public static String minuteText(int minuteOfDayMa) {
        return String.format("%02d:%02d", minuteOfDayMa / 60, minuteOfDayMa % 60);
    }

    /** 120 -> "2 h", 90 -> "1 h 30 min", 45 -> "45 min". */
    public static String minutesText(long minutesMa) {
        if (minutesMa < 60) {
            return minutesMa + " min";
        }
        long hoursMa = minutesMa / 60;
        long restMa = minutesMa % 60;
        return restMa == 0 ? hoursMa + " h" : hoursMa + " h " + restMa + " min";
    }
}
