package com.appcontrol.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Lee un historial fabricado con el formato real de Chrome y de Firefox: cada visita con su hora, solo dominios. */
class BrowserHistoryReadJtTest {

    private static final long CHROMIUM_OFFSET_MS = 11_644_473_600_000L;

    private static long chromeTime(Instant instant) {
        return (instant.toEpochMilli() + CHROMIUM_OFFSET_MS) * 1000;
    }

    @Test
    void chromeDevuelveCadaVisitaConSuHoraYSoloDominios() throws Exception {
        Path db = Files.createTempFile("history-chrome", ".db");
        Instant t0 = Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
                Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE urls (id INTEGER PRIMARY KEY, url TEXT, title TEXT, last_visit_time INTEGER)");
            st.execute("CREATE TABLE visits (id INTEGER PRIMARY KEY, url INTEGER, visit_time INTEGER)");
            st.execute("INSERT INTO urls VALUES (1, 'https://www.facebook.com/messages/t/123?x=1', 'secreto', "
                    + chromeTime(t0.plusSeconds(600)) + ")");
            st.execute("INSERT INTO urls VALUES (2, 'http://localhost:8080/api', 'local', " + chromeTime(t0) + ")");
            st.execute("INSERT INTO urls VALUES (3, 'https://mail.ejemplo.com.pe/inbox', 'x', "
                    + chromeTime(t0.plusSeconds(60)) + ")");
            st.execute("INSERT INTO visits VALUES (1, 1, " + chromeTime(t0) + ")");
            st.execute("INSERT INTO visits VALUES (2, 1, " + chromeTime(t0.plusSeconds(300)) + ")");
            st.execute("INSERT INTO visits VALUES (3, 1, " + chromeTime(t0.plusSeconds(600)) + ")");
            st.execute("INSERT INTO visits VALUES (4, 2, " + chromeTime(t0.plusSeconds(10)) + ")");
            st.execute("INSERT INTO visits VALUES (5, 3, " + chromeTime(t0.plusSeconds(60)) + ")");
        }
        // el lector espera un archivo llamado "History" (Chromium)
        Path source = db.resolveSibling("History");
        Files.copy(db, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            Map<String, Instant> seen = new HashMap<>();
            Map<String, List<Instant>> visits = new HashMap<>();
            BrowserHistoryCollectorJt.readJtMa(source, t0.minusSeconds(60), seen, visits);

            assertEquals(List.of("ejemplo.com.pe", "facebook.com"), new ArrayList<>(visits.keySet().stream().sorted().toList()));
            assertFalse(visits.containsKey("localhost"));
            assertEquals(3, visits.get("facebook.com").size());
            assertEquals(t0.plusSeconds(600), seen.get("facebook.com"));
            assertEquals(1, visits.get("ejemplo.com.pe").size());
            // ni la direccion completa ni el titulo llegan al resultado
            assertTrue(visits.keySet().stream().noneMatch(kJt -> kJt.contains("/") || kJt.contains("secreto")));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(db);
        }
    }

    @Test
    void soloLeeLoPosteriorALaUltimaLectura() throws Exception {
        Path db = Files.createTempFile("history-chrome2", ".db");
        Instant t0 = Instant.now().minusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
                Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE urls (id INTEGER PRIMARY KEY, url TEXT, title TEXT, last_visit_time INTEGER)");
            st.execute("CREATE TABLE visits (id INTEGER PRIMARY KEY, url INTEGER, visit_time INTEGER)");
            st.execute("INSERT INTO urls VALUES (1, 'https://youtube.com/watch', 't', 0)");
            st.execute("INSERT INTO visits VALUES (1, 1, " + chromeTime(t0) + ")");
            st.execute("INSERT INTO visits VALUES (2, 1, " + chromeTime(t0.plusSeconds(3600)) + ")");
        }
        Path source = db.resolveSibling("History");
        Files.copy(db, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            Map<String, List<Instant>> visits = new HashMap<>();
            BrowserHistoryCollectorJt.readJtMa(source, t0.plusSeconds(1800), new HashMap<>(), visits);
            assertEquals(1, visits.get("youtube.com").size());
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(db);
        }
    }

    @Test
    void firefoxTambienDevuelveCadaVisita() throws Exception {
        Path dir = Files.createTempDirectory("ff");
        Path source = dir.resolve("places.sqlite");
        Instant t0 = Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + source.toAbsolutePath());
                Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE moz_places (id INTEGER PRIMARY KEY, url TEXT, last_visit_date INTEGER)");
            st.execute("CREATE TABLE moz_historyvisits (id INTEGER PRIMARY KEY, place_id INTEGER, visit_date INTEGER)");
            st.execute("INSERT INTO moz_places VALUES (1, 'https://www.tiktok.com/@alguien/video/9', 0)");
            st.execute("INSERT INTO moz_historyvisits VALUES (1, 1, " + t0.toEpochMilli() * 1000 + ")");
            st.execute("INSERT INTO moz_historyvisits VALUES (2, 1, " + t0.plusSeconds(90).toEpochMilli() * 1000 + ")");
        }
        try {
            Map<String, List<Instant>> visits = new HashMap<>();
            BrowserHistoryCollectorJt.readJtMa(source, t0.minusSeconds(10), new HashMap<>(), visits);
            assertEquals(2, visits.get("tiktok.com").size());
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    void ademasAprendeLosTitulosParaSaberQueSitioEstaAlFrente() throws Exception {
        Path db = Files.createTempFile("history-chrome3", ".db");
        Instant t0 = Instant.now().minusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
                Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE urls (id INTEGER PRIMARY KEY, url TEXT, title TEXT, last_visit_time INTEGER)");
            st.execute("CREATE TABLE visits (id INTEGER PRIMARY KEY, url INTEGER, visit_time INTEGER)");
            st.execute("INSERT INTO urls VALUES (1, 'https://web.whatsapp.com/', 'WhatsApp', " + chromeTime(t0) + ")");
            st.execute("INSERT INTO urls VALUES (2, 'https://bitlearning.com/curso/1', 'Curso de redes - Bitlearning', "
                    + chromeTime(t0) + ")");
            st.execute("INSERT INTO visits VALUES (1, 1, " + chromeTime(t0) + ")");
        }
        Path source = db.resolveSibling("History");
        Files.copy(db, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try {
            SiteTitleIndexJt index = new SiteTitleIndexJt();
            BrowserHistoryCollectorJt.readJtMa(source, t0.minusSeconds(60), new HashMap<>(), new HashMap<>(), index);
            assertEquals("whatsapp.com", index.domainForJt("(2) WhatsApp - Google Chrome"));
            assertEquals("bitlearning.com", index.domainForJt("Curso de redes - Bitlearning - Google Chrome"));
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(db);
        }
    }
}
