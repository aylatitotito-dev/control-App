package com.appcontrol.tracker;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.SeenSiteJtMa;
import com.appcontrol.service.RuleServiceJt;
import com.appcontrol.service.SiteIconStoreMa;
import com.appcontrol.service.VisitedSitesServiceMa;

/**
 * Toma los iconos (favicons) de los sitios de la cache que ya tienen Chrome, Edge y Brave en este mismo equipo.
 * No hace ninguna conexion a internet. Solo guarda el icono de los dominios que AppControl muestra.
 */
@Component
@ConditionalOnProperty(name = "appcontrol.sites.enabled", havingValue = "true", matchIfMissing = true)
public class FaviconCollectorJt {

    private static final Logger logMa = LoggerFactory.getLogger(FaviconCollectorJt.class);

    private final VisitedSitesServiceMa visitedMa;
    private final RuleServiceJt rulesMa;
    private final boolean windowsMa = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    public FaviconCollectorJt(VisitedSitesServiceMa visitedMa, RuleServiceJt rulesMa) {
        this.visitedMa = visitedMa;
        this.rulesMa = rulesMa;
    }

    @Scheduled(initialDelay = 25_000, fixedDelay = 300_000)
    public synchronized void importMa() {
        if (!windowsMa) {
            return;
        }
        try {
            Set<String> missingMa = new HashSet<>();
            for (SeenSiteJtMa seenMa : visitedMa.list()) {
                if (!SiteIconStoreMa.hasMa(seenMa.getHost())) {
                    missingMa.add(seenMa.getHost());
                }
            }
            for (BlockRuleJtMa ruleMa : rulesMa.listJtMa(BlockRuleJtMa.SITE)) {
                if (!SiteIconStoreMa.hasMa(ruleMa.getTargetKey())) {
                    missingMa.add(ruleMa.getTargetKey());
                }
            }
            if (missingMa.isEmpty()) {
                return;
            }
            Map<String, byte[]> foundMa = new HashMap<>();
            Map<String, Integer> widthsMa = new HashMap<>();
            for (Path fileMa : faviconFilesMa()) {
                try {
                    readMa(fileMa, missingMa, foundMa, widthsMa);
                } catch (Exception exMa) {
                    logMa.debug("No se pudo leer una cache de iconos: {}", exMa.toString());
                }
            }
            if (!foundMa.isEmpty()) {
                Files.createDirectories(SiteIconStoreMa.dirMa());
                for (Map.Entry<String, byte[]> entryMa : foundMa.entrySet()) {
                    Files.write(SiteIconStoreMa.fileForMa(entryMa.getKey()), entryMa.getValue());
                }
                logMa.info("Iconos de sitios guardados: {}", foundMa.size());
            }
        } catch (Exception exMa) {
            logMa.warn("No se pudieron importar los iconos de sitios: {}", exMa.toString());
        }
    }

    private static Set<Path> faviconFilesMa() {
        Set<Path> outMa = new HashSet<>();
        String localMa = System.getenv("LOCALAPPDATA");
        if (localMa == null) {
            return outMa;
        }
        for (String relMa : new String[] {"Google\\Chrome\\User Data", "Microsoft\\Edge\\User Data",
            "BraveSoftware\\Brave-Browser\\User Data"}) {
            Path rootMa = Path.of(localMa, relMa);
            if (!Files.isDirectory(rootMa)) {
                continue;
            }
            try (DirectoryStream<Path> dirsMa = Files.newDirectoryStream(rootMa)) {
                for (Path dirMa : dirsMa) {
                    String nameMa = dirMa.getFileName().toString();
                    Path favMa = dirMa.resolve("Favicons");
                    if ((nameMa.equals("Default") || nameMa.startsWith("Profile ")) && Files.isRegularFile(favMa)) {
                        outMa.add(favMa);
                    }
                }
            } catch (IOException exMa) {
                // carpeta no legible: se omite
            }
        }
        return outMa;
    }

    /** Lee una cache de iconos sobre una copia temporal; se queda con el PNG mas grande (hasta 64 px) de cada dominio. */
    private static void readMa(Path sourceMa, Set<String> wantedMa, Map<String, byte[]> foundMa,
            Map<String, Integer> widthsMa) throws Exception {
        Path copyMa = Files.createTempFile("appcontrol-fav", ".db");
        try {
            Files.copy(sourceMa, copyMa, StandardCopyOption.REPLACE_EXISTING);
            String sqlMa = "SELECT m.page_url, b.image_data, b.width FROM icon_mapping m "
                    + "JOIN favicon_bitmaps b ON b.icon_id = m.icon_id WHERE b.width BETWEEN 16 AND 64";
            try (Connection connMa = DriverManager.getConnection("jdbc:sqlite:" + copyMa.toAbsolutePath());
                    PreparedStatement stMa = connMa.prepareStatement(sqlMa);
                    ResultSet rsMa = stMa.executeQuery()) {
                while (rsMa.next()) {
                    String domainMa = BrowserHistoryCollectorJt.domainOfJtMa(rsMa.getString(1));
                    if (domainMa == null || !wantedMa.contains(domainMa)) {
                        continue;
                    }
                    byte[] dataMa = rsMa.getBytes(2);
                    int widthMa = rsMa.getInt(3);
                    if (SiteIconStoreMa.isPngMa(dataMa) && widthMa > widthsMa.getOrDefault(domainMa, 0)) {
                        foundMa.put(domainMa, dataMa);
                        widthsMa.put(domainMa, widthMa);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(copyMa);
        }
    }
}
