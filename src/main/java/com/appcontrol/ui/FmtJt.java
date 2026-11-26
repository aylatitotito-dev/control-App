package com.appcontrol.ui;

/** Formatos de texto para la interfaz. */
public final class FmtJt {

    private FmtJt() {
    }

    /** "hace 5 min", "hace 3 h", "ayer", o la fecha. */
    public static String agoJtMa(java.time.Instant whenJtMa) {
        long minJtMa = java.time.Duration.between(whenJtMa, java.time.Instant.now()).toMinutes();
        if (minJtMa < 1) {
            return "ahora mismo";
        }
        if (minJtMa < 60) {
            return "hace " + minJtMa + " min";
        }
        if (minJtMa < 24 * 60) {
            return "hace " + (minJtMa / 60) + " h";
        }
        long daysJtMa = minJtMa / (24 * 60);
        return daysJtMa == 1 ? "ayer" : "hace " + daysJtMa + " días";
    }

    /** Tamano estilo Windows: "40.0 KB", "11.0 MB", "677 MB", "1.2 GB". Recibe KB. */
    public static String sizeJtMa(long kbJtMa) {
        if (kbJtMa < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", (double) kbJtMa);
        }
        double mbJtMa = kbJtMa / 1024.0;
        if (mbJtMa < 1024) {
            return mbJtMa < 100 ? String.format(java.util.Locale.ROOT, "%.1f MB", mbJtMa)
                    : String.format(java.util.Locale.ROOT, "%.0f MB", mbJtMa);
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB", mbJtMa / 1024.0);
    }

    public static String durationJtMa(long secondsJtMa) {
        if (secondsJtMa < 60) {
            return secondsJtMa + " s";
        }
        long minJtMa = secondsJtMa / 60;
        if (minJtMa < 60) {
            return minJtMa + " min";
        }
        return (minJtMa / 60) + " h " + (minJtMa % 60) + " min";
    }
}
