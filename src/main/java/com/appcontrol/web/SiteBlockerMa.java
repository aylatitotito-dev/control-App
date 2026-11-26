package com.appcontrol.web;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.ActivityEventJt;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.RuleLogicJtMa;
import com.appcontrol.service.RuleServiceJt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Proxy local (solo 127.0.0.1) al que el navegador manda UNICAMENTE los dominios que tienen una regla.
 * En cada conexion decide si el sitio esta bloqueado ahora (bloqueo total, horario o cuota agotada):
 * si lo esta responde 403; si no, deja pasar el trafico sin leerlo (tunel). Solo ve el nombre del dominio.
 */
@Component
public class SiteBlockerMa {

    private static final Logger logJt = LoggerFactory.getLogger(SiteBlockerMa.class);
    private static final long CACHE_MS_JT = 3_000;
    private static final int MAX_HEAD_JT = 16 * 1024;

    private final RuleServiceJt rulesJt;
    /** Reglas de sitios que vienen de un grupo (opcional). */
    private com.appcontrol.cloud.GroupRulesSourceMa groupRulesJt;
    private final ActivityServiceMa activityJt;
    private final int portJt;
    private final boolean enabledJt;
    private final ExecutorService poolJt = Executors.newCachedThreadPool(rJt -> {
        Thread tJt = new Thread(rJt, "site-proxy");
        tJt.setDaemon(true);
        return tJt;
    });

    private volatile ServerSocket serverJt;
    private volatile List<BlockRuleJtMa> cacheJt = List.of();
    private volatile long cacheAtJt;
    /** clave de regla -> conexiones abiertas ahora (para contar tiempo de uso de cuotas). */
    private final Map<String, AtomicInteger> openJt = new ConcurrentHashMap<>();
    /** clave de regla -> segundos de uso hoy (aproximado: tiempo con conexiones abiertas). */
    private final Map<String, Long> usedJt = new ConcurrentHashMap<>();
    private volatile LocalDate dayJt = LocalDate.now();
    /** clave de regla -> ultimo momento en que se anoto un bloqueo (para no escribir en la base por cada reintento). */
    private final Map<String, Long> noticedJt = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setGroupRulesJt(com.appcontrol.cloud.GroupRulesSourceMa groupRulesJt) {
        this.groupRulesJt = groupRulesJt;
    }

    public SiteBlockerMa(RuleServiceJt rulesJt, ActivityServiceMa activityJt,
            @Value("${appcontrol.sites.proxy-port:8081}") int portJt,
            @Value("${appcontrol.sites.proxy-enabled:true}") boolean enabledJt) {
        this.rulesJt = rulesJt;
        this.activityJt = activityJt;
        this.portJt = portJt;
        this.enabledJt = enabledJt;
    }

    @PostConstruct
    public void startJt() {
        if (!enabledJt || serverJt != null) {
            return;
        }
        try {
            ServerSocket socketJt;
            try {
                socketJt = new ServerSocket(portJt, 100, InetAddress.getLoopbackAddress());
            } catch (IOException busyJt) {
                // El puerto preferido está ocupado (otra copia u otro programa): se usa uno libre; el script PAC lo anuncia.
                logJt.warn("El puerto {} del proxy está ocupado; se usará otro libre.", portJt);
                socketJt = new ServerSocket(0, 100, InetAddress.getLoopbackAddress());
            }
            serverJt = socketJt;
            poolJt.execute(this::acceptLoopJt);
            logJt.info("Proxy de sitios activo en 127.0.0.1:{}", serverJt.getLocalPort());
        } catch (IOException exJt) {
            serverJt = null;
            logJt.warn("No se pudo abrir el proxy de sitios en el puerto {}: {}", portJt, exJt.toString());
        }
    }

    @PreDestroy
    public void stopJt() {
        ServerSocket currentJt = serverJt;
        serverJt = null;
        if (currentJt != null) {
            try {
                currentJt.close();
            } catch (IOException exJt) {
                // ya cerrado
            }
        }
        poolJt.shutdownNow();
    }

    public boolean isRunningJt() {
        ServerSocket currentJt = serverJt;
        return currentJt != null && !currentJt.isClosed();
    }

    public int localPortJt() {
        ServerSocket currentJt = serverJt;
        return currentJt == null ? -1 : currentJt.getLocalPort();
    }

    /** Cada 10 s suma tiempo de uso a las reglas con conexiones abiertas; a medianoche reinicia. */
    @Scheduled(fixedDelay = 10_000)
    public void tickJt() {
        if (!LocalDate.now().equals(dayJt)) {
            dayJt = LocalDate.now();
            usedJt.clear();
        }
        openJt.forEach((keyJt, countJt) -> {
            if (countJt.get() > 0) {
                usedJt.merge(keyJt, 10L, Long::sum);
            }
        });
    }

    public long usedSecondsJt(String keyJt) {
        return usedJt.getOrDefault(keyJt, 0L);
    }

    // ---------------------------------------------------------------- decision

    /** true si "host" cae dentro de la regla (el dominio mismo o cualquier subdominio). */
    public static boolean matchesJt(String hostJt, String keyJt) {
        String lowerJt = hostJt.toLowerCase(Locale.ROOT);
        return lowerJt.equals(keyJt) || lowerJt.endsWith("." + keyJt);
    }

    private List<BlockRuleJtMa> rulesNowJt() {
        long nowMsJt = System.currentTimeMillis();
        if (nowMsJt - cacheAtJt > CACHE_MS_JT) {
            try {
                List<BlockRuleJtMa> allJt = new java.util.ArrayList<>(rulesJt.listJtMa(BlockRuleJtMa.SITE));
                if (groupRulesJt != null) {
                    allJt.addAll(groupRulesJt.activeJt(BlockRuleJtMa.SITE));
                }
                cacheJt = allJt;
            } catch (Exception exJt) {
                logJt.debug("No se pudieron leer las reglas de sitios: {}", exJt.toString());
            }
            cacheAtJt = nowMsJt;
        }
        return cacheJt;
    }

    private BlockRuleJtMa ruleFor(String hostJt) {
        for (BlockRuleJtMa ruleJt : rulesNowJt()) {
            if (matchesJt(hostJt, ruleJt.getTargetKey())) {
                return ruleJt;
            }
        }
        return null;
    }

    /** true si ahora mismo el sitio debe estar bloqueado. */
    public boolean isBlockedJt(String hostJt) {
        BlockRuleJtMa ruleJt = ruleFor(hostJt);
        if (ruleJt == null) {
            return false;
        }
        RuleLogicJtMa.StatusMa statusJt = RuleLogicJtMa.evaluateMa(ruleJt, LocalDateTime.now(), Instant.now(),
                usedSecondsJt(ruleJt.getTargetKey()));
        return statusJt.enforcing() && statusJt.blockedNow();
    }

    // ---------------------------------------------------------------- conexiones

    private void acceptLoopJt() {
        ServerSocket serverNowJt;
        while ((serverNowJt = serverJt) != null && !serverNowJt.isClosed()) {
            try {
                Socket clientJt = serverNowJt.accept();
                poolJt.execute(() -> handleJt(clientJt));
            } catch (IOException exJt) {
                if (serverJt == null) {
                    return;
                }
            }
        }
    }

    private void handleJt(Socket clientJt) {
        try (clientJt) {
            clientJt.setSoTimeout(20_000);
            BufferedInputStream inJt = new BufferedInputStream(clientJt.getInputStream());
            OutputStream outJt = clientJt.getOutputStream();
            String headJt = readHeadJt(inJt);
            if (headJt == null) {
                return;
            }
            String firstJt = headJt.substring(0, headJt.indexOf("\r\n") < 0 ? headJt.length() : headJt.indexOf("\r\n"));
            String[] partsJt = firstJt.split(" ");
            if (partsJt.length < 2) {
                return;
            }
            if ("CONNECT".equalsIgnoreCase(partsJt[0])) {
                handleConnectJt(partsJt[1], inJt, outJt, clientJt);
            } else {
                handlePlainJt(partsJt[0], partsJt[1], partsJt.length > 2 ? partsJt[2] : "HTTP/1.1", headJt, inJt,
                        outJt, clientJt);
            }
        } catch (IOException exJt) {
            // conexion cerrada por el navegador: normal
        }
    }

    private void handleConnectJt(String targetJt, InputStream inJt, OutputStream outJt, Socket clientJt)
            throws IOException {
        int colonJt = targetJt.lastIndexOf(':');
        String hostJt = colonJt > 0 ? targetJt.substring(0, colonJt) : targetJt;
        int remotePortJt = colonJt > 0 ? parsePortJt(targetJt.substring(colonJt + 1), 443) : 443;
        if (isBlockedJt(hostJt)) {
            noteBlockJt(hostJt);
            denyJt(outJt, true);
            return;
        }
        BlockRuleJtMa ruleJt = ruleFor(hostJt);
        String keyJt = ruleJt == null ? null : ruleJt.getTargetKey();
        try (Socket remoteJt = new Socket()) {
            remoteJt.connect(new InetSocketAddress(hostJt, remotePortJt), 10_000);
            remoteJt.setSoTimeout(0);
            clientJt.setSoTimeout(0);
            outJt.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            outJt.flush();
            track(keyJt, 1);
            try {
                pipeBothJt(inJt, outJt, remoteJt);
            } finally {
                track(keyJt, -1);
            }
        } catch (IOException exJt) {
            outJt.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            outJt.flush();
        }
    }

    private void handlePlainJt(String methodJt, String targetJt, String versionJt, String headJt, InputStream inJt,
            OutputStream outJt, Socket clientJt) throws IOException {
        URI uriJt;
        try {
            uriJt = URI.create(targetJt);
        } catch (IllegalArgumentException exJt) {
            return;
        }
        String hostJt = uriJt.getHost();
        if (hostJt == null) {
            // peticion directa al proxy (no es un uso valido)
            outJt.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            return;
        }
        if (isBlockedJt(hostJt)) {
            noteBlockJt(hostJt);
            denyJt(outJt, false);
            return;
        }
        int remotePortJt = uriJt.getPort() > 0 ? uriJt.getPort() : 80;
        String pathJt = (uriJt.getRawPath() == null || uriJt.getRawPath().isEmpty() ? "/" : uriJt.getRawPath())
                + (uriJt.getRawQuery() == null ? "" : "?" + uriJt.getRawQuery());
        StringBuilder rewrittenJt = new StringBuilder(methodJt).append(' ').append(pathJt).append(' ')
                .append(versionJt).append("\r\n");
        boolean skippedFirstJt = false;
        for (String lineJt : headJt.split("\r\n")) {
            if (!skippedFirstJt) {
                skippedFirstJt = true;
                continue;
            }
            String lowerJt = lineJt.toLowerCase(Locale.ROOT);
            if (lineJt.isEmpty() || lowerJt.startsWith("proxy-connection:") || lowerJt.startsWith("connection:")) {
                continue;
            }
            rewrittenJt.append(lineJt).append("\r\n");
        }
        rewrittenJt.append("Connection: close\r\n\r\n");

        BlockRuleJtMa ruleJt = ruleFor(hostJt);
        String keyJt = ruleJt == null ? null : ruleJt.getTargetKey();
        try (Socket remoteJt = new Socket()) {
            remoteJt.connect(new InetSocketAddress(hostJt, remotePortJt), 10_000);
            remoteJt.setSoTimeout(0);
            clientJt.setSoTimeout(0);
            remoteJt.getOutputStream().write(rewrittenJt.toString().getBytes(StandardCharsets.ISO_8859_1));
            remoteJt.getOutputStream().flush();
            track(keyJt, 1);
            try {
                pipeBothJt(inJt, outJt, remoteJt);
            } finally {
                track(keyJt, -1);
            }
        } catch (IOException exJt) {
            outJt.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            outJt.flush();
        }
    }

    /** Anota en el historial que se rechazo un sitio (a lo mas una vez cada 10 s por regla, sin frenar al navegador). */
    private void noteBlockJt(String hostJt) {
        BlockRuleJtMa ruleJt = ruleFor(hostJt);
        if (ruleJt == null) {
            return;
        }
        long nowMsJt = System.currentTimeMillis();
        Long lastJt = noticedJt.put(ruleJt.getTargetKey(), nowMsJt);
        if (lastJt != null && nowMsJt - lastJt < 10_000) {
            noticedJt.put(ruleJt.getTargetKey(), lastJt);
            return;
        }
        poolJt.execute(() -> {
            try {
                activityJt.recordBlockJt(ActivityEventJt.BLOCK_SITE, ruleJt.getTargetKey(), ruleJt.getTargetKey(),
                        Instant.now());
            } catch (Exception exJt) {
                logJt.debug("No se pudo anotar el bloqueo: {}", exJt.toString());
            }
        });
    }

    private void track(String keyJt, int deltaJt) {
        if (keyJt != null) {
            openJt.computeIfAbsent(keyJt, kJt -> new AtomicInteger()).addAndGet(deltaJt);
        }
    }

    /** Copia en las dos direcciones hasta que alguna se cierre. */
    private void pipeBothJt(InputStream clientInJt, OutputStream clientOutJt, Socket remoteJt) throws IOException {
        Thread upJt = new Thread(() -> {
            try {
                clientInJt.transferTo(remoteJt.getOutputStream());
            } catch (IOException exJt) {
                // cierre normal
            } finally {
                try {
                    remoteJt.shutdownOutput();
                } catch (IOException exJt) {
                    // ya cerrado
                }
            }
        }, "site-proxy-up");
        upJt.setDaemon(true);
        upJt.start();
        try {
            remoteJt.getInputStream().transferTo(clientOutJt);
        } catch (IOException exJt) {
            // cierre normal
        }
        clientOutJt.flush();
    }

    private static void denyJt(OutputStream outJt, boolean connectJt) throws IOException {
        String bodyJt = "<html><head><meta charset=\"utf-8\"><title>Sitio bloqueado</title></head>"
                + "<body style=\"font-family:Segoe UI,sans-serif;text-align:center;margin-top:15%\">"
                + "<h1>Sitio bloqueado por AppControl</h1><p>Este sitio está bloqueado por una regla activa.</p>"
                + "</body></html>";
        byte[] bodyBytesJt = bodyJt.getBytes(StandardCharsets.UTF_8);
        String headJt = "HTTP/1.1 403 Forbidden\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n"
                + "Content-Length: " + (connectJt ? 0 : bodyBytesJt.length) + "\r\n\r\n";
        outJt.write(headJt.getBytes(StandardCharsets.US_ASCII));
        if (!connectJt) {
            outJt.write(bodyBytesJt);
        }
        outJt.flush();
    }

    private static int parsePortJt(String textJt, int defaultJt) {
        try {
            return Integer.parseInt(textJt.trim());
        } catch (NumberFormatException exJt) {
            return defaultJt;
        }
    }

    /** Lee hasta la linea en blanco que termina las cabeceras (byte a byte, sin pasarse). */
    private static String readHeadJt(InputStream inJt) throws IOException {
        ByteArrayOutputStream bufJt = new ByteArrayOutputStream();
        int lastFourJt = 0;
        int bJt;
        while ((bJt = inJt.read()) != -1) {
            bufJt.write(bJt);
            lastFourJt = (lastFourJt << 8) | bJt;
            if (lastFourJt == 0x0D0A0D0A) {
                return bufJt.toString(StandardCharsets.ISO_8859_1);
            }
            if (bufJt.size() > MAX_HEAD_JT) {
                return null;
            }
        }
        return null;
    }
}
