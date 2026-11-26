package com.appcontrol.tracker;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Traduce el titulo de la ventana de un navegador al dominio de la pestana que esta al frente,
 * comparandolo con los titulos que ya estan en el historial del propio navegador.
 * Todo vive en memoria: ni el titulo de la ventana ni los del historial se escriben en ningun registro,
 * y de aqui solo sale un dominio.
 */
@Component
public class SiteTitleIndexJt {

    private static final Set<String> BROWSERS_JT = Set.of("chrome", "msedge", "brave", "firefox", "opera", "vivaldi");
    private static final Set<String> GENERIC_JT = Set.of("new tab", "nueva pestaña", "untitled", "sin título",
            "about:blank", "google chrome", "microsoft edge", "mozilla firefox", "brave");
    private static final Pattern COUNT_PREFIX_JT = Pattern.compile("^(\\(\\d+\\)|\\[\\d+\\])\\s*");
    private static final Pattern MORE_PAGES_JT = Pattern.compile("\\s+(and|y)\\s+\\d+\\s+(more\\s+pages?|p[aá]ginas?\\s+m[aá]s)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR_JT = Pattern.compile("\\s+[-–—|]\\s+");
    private static final int MAX_ENTRIES_JT = 20_000;

    /** titulo normalizado -> dominio ("" si el mismo titulo aparece en dominios distintos). */
    private final Map<String, String> byTitleJt = new ConcurrentHashMap<>();

    public static boolean isBrowserJt(String exeLowerJt) {
        return exeLowerJt != null && BROWSERS_JT.contains(exeLowerJt);
    }

    /** Minusculas, sin contador de notificaciones al inicio ("(3) WhatsApp"), sin espacios raros ni de mas. */
    static String normalizeJt(String rawJt) {
        if (rawJt == null) {
            return "";
        }
        String sJt = rawJt.replace(' ', ' ').replace("​", "").replace("‎", "").trim()
                .toLowerCase(Locale.ROOT);
        sJt = COUNT_PREFIX_JT.matcher(sJt).replaceFirst("");
        return sJt.replaceAll("\\s+", " ").trim();
    }

    /** Aprende "este titulo pertenece a este dominio" (desde el historial del navegador). */
    public void learnJt(String titleJt, String domainJt) {
        String keyJt = normalizeJt(titleJt);
        if (keyJt.length() < 3 || GENERIC_JT.contains(keyJt) || domainJt == null) {
            return;
        }
        if (byTitleJt.size() >= MAX_ENTRIES_JT && !byTitleJt.containsKey(keyJt)) {
            return;
        }
        byTitleJt.merge(keyJt, domainJt, (oldJt, newJt) -> oldJt.equals(newJt) ? oldJt : "");
    }

    /**
     * Dominio de la pestana al frente segun el titulo de la ventana ("WhatsApp - Google Chrome" -> whatsapp.com),
     * o null si no se puede saber con seguridad (titulo desconocido, repetido en varios sitios, pestana nueva).
     */
    public String domainForJt(String windowTitleJt) {
        if (windowTitleJt == null || windowTitleJt.isBlank()) {
            return null;
        }
        String currentJt = MORE_PAGES_JT.matcher(windowTitleJt).replaceAll("");
        while (true) {
            String hitJt = byTitleJt.get(normalizeJt(currentJt));
            if (hitJt != null) {
                return hitJt.isEmpty() ? null : hitJt;
            }
            // quita el ultimo tramo ("- Google Chrome", "- Persona 1") y vuelve a intentar
            java.util.regex.Matcher mJt = SEPARATOR_JT.matcher(currentJt);
            int lastStartJt = -1;
            while (mJt.find()) {
                lastStartJt = mJt.start();
            }
            if (lastStartJt <= 0) {
                return null;
            }
            currentJt = currentJt.substring(0, lastStartJt);
        }
    }

    public int sizeJt() {
        return byTitleJt.size();
    }
}
