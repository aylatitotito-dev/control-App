package com.appcontrol.tracker;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.VisitedSitesServiceMa;

/**
 * Lee del historial de los navegadores SOLO el dominio y la fecha de cada visita
 * (nunca guarda la direccion completa, el titulo ni el contenido). Trabaja sobre una copia
 * temporal del archivo del navegador, que se borra al terminar.
 */
@Component
@ConditionalOnProperty(name = "appcontrol.sites.enabled", havingValue = "true", matchIfMissing = true)
public class BrowserHistoryCollectorJt {

    private static final Logger logJtMa = LoggerFactory.getLogger(BrowserHistoryCollectorJt.class);

    /** Microsegundos entre 1601-01-01 (epoca de Chromium) y 1970-01-01. */
    private static final long CHROMIUM_EPOCH_OFFSET_MS_JTMA = 11_644_473_600_000L;
    private static final Set<String> SECOND_LEVEL_JTMA = Set.of("co", "com", "org", "net", "gov", "edu", "ac", "gob");
    /** Dominios de infraestructura que no son sitios que el usuario "visita". */
    private static final Set<String> IGNORED_JTMA = Set.of("gstatic.com", "googleapis.com", "googleusercontent.com",
            "doubleclick.net", "googlesyndication.com", "google-analytics.com", "googletagmanager.com",
            "akamaihd.net", "akamaized.net", "cloudfront.net", "ytimg.com", "windowsupdate.com",
            "msftconnecttest.com", "gvt1.com", "gvt2.com");

    private final VisitedSitesServiceMa sitesJtMa;
    private final ActivityServiceMa activityJtMa;
    private final SiteTitleIndexJt titlesJtMa;
    private final boolean windowsJtMa = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private Instant lastScanJtMa = Instant.now().minus(Duration.ofDays(7));

    public BrowserHistoryCollectorJt(VisitedSitesServiceMa sitesJtMa, ActivityServiceMa activityJtMa,
            SiteTitleIndexJt titlesJtMa) {
        this.sitesJtMa = sitesJtMa;
        this.activityJtMa = activityJtMa;
        this.titlesJtMa = titlesJtMa;
    }

    @Scheduled(initialDelay = 10_000, fixedDelayString = "${appcontrol.sites.interval-ms:120000}")
    public synchronized void collectJtMa() {
        if (!windowsJtMa) {
            return;
        }
        Instant sinceJtMa = lastScanJtMa;
        Instant startJtMa = Instant.now();
        Map<String, Instant> seenJtMa = new HashMap<>();
        Map<String, List<Instant>> visitsJtMa = new HashMap<>();
        for (Path fileJtMa : historyFilesJtMa()) {
            try {
                readJtMa(fileJtMa, sinceJtMa, seenJtMa, visitsJtMa, titlesJtMa);
            } catch (Exception exJtMa) {
                logJtMa.debug("No se pudo leer un historial: {}", exJtMa.toString());
            }
        }
        if (!seenJtMa.isEmpty()) {
            sitesJtMa.recordJt(seenJtMa);
            activityJtMa.recordSiteVisitsJt(visitsJtMa);
        }
        lastScanJtMa = startJtMa;
    }

    private static List<Path> historyFilesJtMa() {
        List<Path> outJtMa = new ArrayList<>();
        String localJtMa = System.getenv("LOCALAPPDATA");
        String roamingJtMa = System.getenv("APPDATA");
        if (localJtMa != null) {
            for (String relJtMa : new String[] {"Google\\Chrome\\User Data", "Microsoft\\Edge\\User Data",
                "BraveSoftware\\Brave-Browser\\User Data"}) {
                collectProfilesJtMa(Path.of(localJtMa, relJtMa), "History", true, outJtMa);
            }
        }
        if (roamingJtMa != null) {
            collectProfilesJtMa(Path.of(roamingJtMa, "Mozilla", "Firefox", "Profiles"), "places.sqlite", false, outJtMa);
        }
        return outJtMa;
    }

    private static void collectProfilesJtMa(Path rootJtMa, String fileNameJtMa, boolean chromiumJtMa, List<Path> outJtMa) {
        if (!Files.isDirectory(rootJtMa)) {
            return;
        }
        try (DirectoryStream<Path> dirsJtMa = Files.newDirectoryStream(rootJtMa)) {
            for (Path dirJtMa : dirsJtMa) {
                String nameJtMa = dirJtMa.getFileName().toString();
                boolean okJtMa = !chromiumJtMa || nameJtMa.equals("Default") || nameJtMa.startsWith("Profile ");
                Path fileJtMa = dirJtMa.resolve(fileNameJtMa);
                if (okJtMa && Files.isRegularFile(fileJtMa)) {
                    outJtMa.add(fileJtMa);
                }
            }
        } catch (IOException exJtMa) {
            // carpeta no legible: se omite
        }
    }

    static void readJtMa(Path sourceJtMa, Instant sinceJtMa, Map<String, Instant> seenJtMa,
            Map<String, List<Instant>> visitsJtMa) throws Exception {
        readJtMa(sourceJtMa, sinceJtMa, seenJtMa, visitsJtMa, null);
    }

    static void readJtMa(Path sourceJtMa, Instant sinceJtMa, Map<String, Instant> seenJtMa,
            Map<String, List<Instant>> visitsJtMa, SiteTitleIndexJt titlesJtMa) throws Exception {
        boolean firefoxJtMa = sourceJtMa.getFileName().toString().equals("places.sqlite");
        Path copyJtMa = Files.createTempFile("appcontrol-hist", ".db");
        try {
            Files.copy(sourceJtMa, copyJtMa, StandardCopyOption.REPLACE_EXISTING);
            String sqlJtMa = firefoxJtMa
                    ? "SELECT p.url, h.visit_date FROM moz_historyvisits h JOIN moz_places p ON p.id = h.place_id "
                            + "WHERE h.visit_date > ?"
                    : "SELECT u.url, v.visit_time FROM visits v JOIN urls u ON u.id = v.url WHERE v.visit_time > ?";
            long sinceMicrosJtMa = firefoxJtMa ? sinceJtMa.toEpochMilli() * 1000
                    : (sinceJtMa.toEpochMilli() + CHROMIUM_EPOCH_OFFSET_MS_JTMA) * 1000;
            try (Connection connJtMa = DriverManager.getConnection("jdbc:sqlite:" + copyJtMa.toAbsolutePath());
                    PreparedStatement stJtMa = connJtMa.prepareStatement(sqlJtMa)) {
                stJtMa.setLong(1, sinceMicrosJtMa);
                try (ResultSet rsJtMa = stJtMa.executeQuery()) {
                    while (rsJtMa.next()) {
                        String domainJtMa = domainOfJtMa(rsJtMa.getString(1));
                        if (domainJtMa == null) {
                            continue;
                        }
                        long microsJtMa = rsJtMa.getLong(2);
                        long epochMsJtMa = firefoxJtMa ? microsJtMa / 1000 : microsJtMa / 1000 - CHROMIUM_EPOCH_OFFSET_MS_JTMA;
                        Instant visitJtMa = Instant.ofEpochMilli(epochMsJtMa);
                        seenJtMa.merge(domainJtMa, visitJtMa, (aJtMa, bJtMa) -> aJtMa.isAfter(bJtMa) ? aJtMa : bJtMa);
                        visitsJtMa.computeIfAbsent(domainJtMa, kJtMa -> new ArrayList<>()).add(visitJtMa);
                    }
                }
            }
            if (titlesJtMa != null) {
                learnTitlesJtMa(copyJtMa, firefoxJtMa, titlesJtMa);
            }
        } finally {
            Files.deleteIfExists(copyJtMa);
        }
    }

    /**
     * Aprende (solo en memoria) que titulo de pagina corresponde a cada dominio, con las ultimas paginas del
     * historial. Sirve para saber que sitio esta al frente cuando solo se ve el titulo de la ventana.
     */
    private static void learnTitlesJtMa(Path copyJtMa, boolean firefoxJtMa, SiteTitleIndexJt titlesJtMa) throws Exception {
        String sqlJtMa = firefoxJtMa
                ? "SELECT url, title FROM moz_places WHERE title IS NOT NULL AND title <> '' "
                        + "ORDER BY last_visit_date DESC LIMIT 4000"
                : "SELECT url, title FROM urls WHERE title IS NOT NULL AND title <> '' "
                        + "ORDER BY last_visit_time DESC LIMIT 4000";
        try (Connection connJtMa = DriverManager.getConnection("jdbc:sqlite:" + copyJtMa.toAbsolutePath());
                PreparedStatement stJtMa = connJtMa.prepareStatement(sqlJtMa);
                ResultSet rsJtMa = stJtMa.executeQuery()) {
            while (rsJtMa.next()) {
                titlesJtMa.learnJt(rsJtMa.getString(2), domainOfJtMa(rsJtMa.getString(1)));
            }
        }
    }

    /**
     * Devuelve solo el dominio principal ("www.youtube.com:443/watch?v=1" -> "youtube.com"),
     * o null si no es un sitio web real (IP, local, interno, infraestructura).
     */
    public static String domainOfJtMa(String urlJtMa) {
        if (urlJtMa == null) {
            return null;
        }
        try {
            URI uriJtMa = new URI(urlJtMa.trim());
            String schemeJtMa = uriJtMa.getScheme();
            if (schemeJtMa == null || !(schemeJtMa.equals("http") || schemeJtMa.equals("https"))) {
                return null;
            }
            String hostJtMa = uriJtMa.getHost();
            if (hostJtMa == null) {
                return null;
            }
            hostJtMa = hostJtMa.toLowerCase(Locale.ROOT);
            if (hostJtMa.endsWith(".")) {
                hostJtMa = hostJtMa.substring(0, hostJtMa.length() - 1);
            }
            if (!hostJtMa.contains(".") || hostJtMa.matches("[0-9.]+") || hostJtMa.contains(":")
                    || hostJtMa.endsWith(".local") || hostJtMa.endsWith(".localhost") || hostJtMa.endsWith(".arpa")
                    || hostJtMa.endsWith(".internal")) {
                return null;
            }
            String[] partsJtMa = hostJtMa.split("\\.");
            int nJtMa = partsJtMa.length;
            String regJtMa;
            if (nJtMa >= 3 && partsJtMa[nJtMa - 1].length() == 2 && SECOND_LEVEL_JTMA.contains(partsJtMa[nJtMa - 2])) {
                regJtMa = partsJtMa[nJtMa - 3] + "." + partsJtMa[nJtMa - 2] + "." + partsJtMa[nJtMa - 1];
            } else {
                regJtMa = partsJtMa[nJtMa - 2] + "." + partsJtMa[nJtMa - 1];
            }
            return IGNORED_JTMA.contains(regJtMa) ? null : regJtMa;
        } catch (Exception exJtMa) {
            return null;
        }
    }
}
