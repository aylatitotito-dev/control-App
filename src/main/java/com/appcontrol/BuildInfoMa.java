package com.appcontrol;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Cuándo se armó la copia que está corriendo. Maven escribe ese número en `build.properties` (año, mes, día, hora, minuto y
 * segundo en UTC). Sirve para comprobar de un vistazo que se usa la versión nueva y para saber si hay una actualización
 * más reciente (se compara este número con el que ofrece el servidor).
 */
public final class BuildInfoMa {

    private static final DateTimeFormatter ID_MA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter LABEL_MA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final String BUILD_ID_MA = readIdMa();

    private BuildInfoMa() {
    }

    /** Número de compilación (14 dígitos), o "dev" si se corre desde el código sin compilar con Maven. */
    public static String buildIdMa() {
        return BUILD_ID_MA;
    }

    public static boolean isDevMa() {
        return !BUILD_ID_MA.matches("\\d{14}");
    }

    /** Por ejemplo: "0.0.1 · 25/11 10:31". */
    public static String labelMa() {
        return "0.0.1" + (isDevMa() ? "" : " · " + localTextMa(BUILD_ID_MA));
    }

    /** La fecha y hora (de este equipo) que corresponde a un número de compilación. */
    public static String localTextMa(String buildIdMa) {
        try {
            LocalDateTime utcMa = LocalDateTime.parse(buildIdMa, ID_MA);
            return LABEL_MA.format(utcMa.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()));
        } catch (RuntimeException exMa) {
            return buildIdMa;
        }
    }

    private static String readIdMa() {
        try (InputStream streamMa = BuildInfoMa.class.getResourceAsStream("/build.properties")) {
            if (streamMa != null) {
                Properties propsMa = new Properties();
                propsMa.load(streamMa);
                String rawMa = propsMa.getProperty("build", "dev").trim();
                // Maven puede escribirlo como 20261125153525 o como 2026-11-25T15:35:25Z: se deja solo los dígitos
                String digitsMa = rawMa.replaceAll("[^0-9]", "");
                return digitsMa.length() == 14 ? digitsMa : "dev";
            }
        } catch (Exception exMa) {
            // sin número: se trata como desarrollo
        }
        return "dev";
    }
}
