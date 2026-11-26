package com.appcontrol.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.service.AppSettingsServiceMa;
import com.appcontrol.service.RuleLogicJtMa;
import com.appcontrol.service.RuleServiceJt;

import jakarta.annotation.PreDestroy;

/**
 * Le indica al navegador (por la configuracion de proxy del usuario, sin administrador) que mande SOLO los dominios
 * con regla al proxy de AppControl. Lo demas va directo. Cuando no hay reglas de sitios, o se apaga, o se cierra
 * AppControl, deja la configuracion como estaba. Si el usuario ya tiene su propio proxy o script, no lo pisa.
 */
@Component
public class SiteBlockingJtMa {

    /** Estado para mostrar en Ajustes. */
    public enum StatusMa { ACTIVE, NO_RULES, OFF, PROXY_DOWN, CONFLICT, UNSUPPORTED }

    public static final String SETTING_KEY_JT = "site_blocking";

    private static final Logger logJt = LoggerFactory.getLogger(SiteBlockingJtMa.class);
    private static final String REG_KEY_JT = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    private static final String PAC_PATH_JT = "/proxy.pac";

    private final RuleServiceJt rulesJt;
    /** Reglas de sitios que vienen de un grupo (opcional). */
    private com.appcontrol.cloud.GroupRulesSourceMa groupRulesJt;
    private final AppSettingsServiceMa settingsJt;
    private final SiteBlockerMa proxyJt;
    private final int httpPortJt;
    private final boolean windowsJt = System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    private volatile StatusMa statusJt = StatusMa.NO_RULES;
    private volatile String pacJt = buildPacJt(List.of(), 0);
    private String lastPacJt = "";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setGroupRulesJt(com.appcontrol.cloud.GroupRulesSourceMa groupRulesJt) {
        this.groupRulesJt = groupRulesJt;
    }

    public SiteBlockingJtMa(RuleServiceJt rulesJt, AppSettingsServiceMa settingsJt, SiteBlockerMa proxyJt,
            @Value("${server.port:8080}") int httpPortJt) {
        this.rulesJt = rulesJt;
        this.settingsJt = settingsJt;
        this.proxyJt = proxyJt;
        this.httpPortJt = httpPortJt;
    }

    public StatusMa statusJtMa() {
        return statusJt;
    }

    /** Contenido del script de configuracion automatica que se sirve en /proxy.pac. */
    public String pacJtMa() {
        return pacJt;
    }

    public boolean isEnabledJtMa() {
        return settingsJt.getBoolean(SETTING_KEY_JT, true);
    }

    public void setEnabledJtMa(boolean enabledJt) {
        settingsJt.set(SETTING_KEY_JT, String.valueOf(enabledJt));
        syncJtMa();
    }

    /** Genera el script: los dominios con regla van al proxy local; todo lo demas, directo. */
    static String buildPacJt(List<String> domainsJt, int proxyPortJt) {
        StringBuilder sbJt = new StringBuilder("function FindProxyForURL(url, host) {\n  host = host.toLowerCase();\n");
        sbJt.append("  var blocked = [");
        for (int posJt = 0; posJt < domainsJt.size(); posJt++) {
            sbJt.append(posJt > 0 ? ", " : "").append('"').append(domainsJt.get(posJt)).append('"');
        }
        sbJt.append("];\n  for (var i = 0; i < blocked.length; i++) {\n")
                .append("    if (host == blocked[i] || dnsDomainIs(host, \".\" + blocked[i])) {\n")
                .append("      return \"PROXY 127.0.0.1:").append(proxyPortJt).append("\";\n    }\n  }\n")
                .append("  return \"DIRECT\";\n}\n");
        return sbJt.toString();
    }

    private String ourUrlJt() {
        return "http://127.0.0.1:" + httpPortJt + PAC_PATH_JT;
    }

    /** Revisa cada pocos segundos si hay que activar, actualizar o quitar la configuracion del navegador. */
    @Scheduled(initialDelay = 6_000, fixedDelayString = "${appcontrol.sites.sync-ms:5000}")
    public synchronized void syncJtMa() {
        doSyncJt();
        if (groupRulesJt != null) {
            groupRulesJt.reportStatusMa("sitios", statusJt.name());
        }
    }

    private void doSyncJt() {
        if (!windowsJt) {
            statusJt = StatusMa.UNSUPPORTED;
            return;
        }
        try {
            boolean enabledJt = isEnabledJtMa();
            List<String> domainsJt = enforcingDomainsJt();
            String currentJt = readRegJt("AutoConfigURL");
            boolean oursJt = currentJt != null && currentJt.startsWith("http://127.0.0.1:") && currentJt.endsWith(PAC_PATH_JT);

            if (!enabledJt || domainsJt.isEmpty() || !proxyJt.isRunningJt()) {
                if (oursJt) {
                    removeJt();
                }
                statusJt = !enabledJt ? StatusMa.OFF : domainsJt.isEmpty() ? StatusMa.NO_RULES : StatusMa.PROXY_DOWN;
                return;
            }
            String manualJt = readRegJt("ProxyEnable");
            boolean manualProxyJt = manualJt != null && manualJt.endsWith("0x1");
            if ((currentJt != null && !oursJt) || manualProxyJt) {
                statusJt = StatusMa.CONFLICT;
                return;
            }
            pacJt = buildPacJt(domainsJt, proxyJt.localPortJt());
            if (!oursJt) {
                writeRegJt(ourUrlJt());
                WinInetJt.refreshJt();
                logJt.info("Bloqueo de sitios activado para: {}", domainsJt);
            } else if (!pacJt.equals(lastPacJt)) {
                WinInetJt.refreshJt();
            }
            lastPacJt = pacJt;
            statusJt = StatusMa.ACTIVE;
        } catch (Exception exJt) {
            logJt.warn("No se pudo actualizar el bloqueo de sitios: {}", exJt.toString());
        }
    }

    /** Dominios con regla que se estan aplicando (no desactivadas ni en pausa). El proxy decide el momento exacto. */
    private List<String> enforcingDomainsJt() {
        List<String> outJt = new ArrayList<>();
        LocalDateTime nowJt = LocalDateTime.now();
        Instant instantJt = Instant.now();
        List<BlockRuleJtMa> allJt = new ArrayList<>(rulesJt.listJtMa(BlockRuleJtMa.SITE));
        if (groupRulesJt != null) {
            allJt.addAll(groupRulesJt.activeJt(BlockRuleJtMa.SITE));
        }
        for (BlockRuleJtMa ruleJt : allJt) {
            if (RuleLogicJtMa.evaluateMa(ruleJt, nowJt, instantJt, 0).enforcing()) {
                outJt.add(ruleJt.getTargetKey());
            }
        }
        return outJt;
    }

    /** Al cerrar AppControl deja el navegador como estaba. */
    @PreDestroy
    public synchronized void restoreJtMa() {
        if (!windowsJt) {
            return;
        }
        try {
            String currentJt = readRegJt("AutoConfigURL");
            if (currentJt != null && currentJt.startsWith("http://127.0.0.1:") && currentJt.endsWith(PAC_PATH_JT)) {
                removeJt();
            }
        } catch (Exception exJt) {
            logJt.warn("No se pudo restaurar la configuracion del navegador: {}", exJt.toString());
        }
    }

    // ---------------------------------------------------------------- registro de Windows (reg.exe)

    private void removeJt() throws IOException {
        runRegJt("delete", REG_KEY_JT, "/v", "AutoConfigURL", "/f");
        WinInetJt.refreshJt();
        lastPacJt = "";
        logJt.info("Bloqueo de sitios desactivado: configuracion del navegador restaurada");
    }

    private void writeRegJt(String urlJt) throws IOException {
        runRegJt("add", REG_KEY_JT, "/v", "AutoConfigURL", "/t", "REG_SZ", "/d", urlJt, "/f");
    }

    /** Valor de una entrada del registro o null si no existe. */
    private static String readRegJt(String nameJt) throws IOException {
        List<String> linesJt = runRegJt("query", REG_KEY_JT, "/v", nameJt);
        for (String lineJt : linesJt) {
            int idxJt = lineJt.indexOf("REG_");
            if (lineJt.trim().startsWith(nameJt) && idxJt > 0) {
                String restJt = lineJt.substring(idxJt);
                int spaceJt = restJt.indexOf(' ');
                return spaceJt < 0 ? "" : restJt.substring(spaceJt).trim();
            }
        }
        return null;
    }

    private static List<String> runRegJt(String... argsJt) throws IOException {
        List<String> cmdJt = new ArrayList<>(List.of("reg"));
        cmdJt.addAll(List.of(argsJt));
        Process procJt = new ProcessBuilder(cmdJt).redirectErrorStream(true).start();
        List<String> outJt = new ArrayList<>();
        try (BufferedReader rJt = new BufferedReader(
                new InputStreamReader(procJt.getInputStream(), StandardCharsets.UTF_8))) {
            String lineJt;
            while ((lineJt = rJt.readLine()) != null) {
                outJt.add(lineJt);
            }
        }
        try {
            procJt.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException exJt) {
            Thread.currentThread().interrupt();
        }
        return outJt;
    }
}
