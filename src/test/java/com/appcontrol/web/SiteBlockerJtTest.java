package com.appcontrol.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;
import com.appcontrol.model.RuleStateJt;
import com.appcontrol.service.ActivityServiceMa;
import com.appcontrol.service.RuleServiceJt;

/** Pruebas del proxy de sitios con un "Facebook" bloqueado y un servidor local que SI debe pasar. */
class SiteBlockerJtTest {

    private SiteBlockerMa blockerMa;
    private ServerSocket echoMa;

    private static BlockRuleJtMa siteRule(String hostMa, RuleModeMa modeMa, int daysMa, int startMa, int endMa) {
        BlockRuleJtMa ruleMa = new BlockRuleJtMa(BlockRuleJtMa.SITE, hostMa, hostMa);
        ruleMa.configureJt(modeMa, daysMa, startMa, endMa, 0);
        return ruleMa;
    }

    private void startWith(BlockRuleJtMa... rulesMa) {
        RuleServiceJt serviceMa = mock(RuleServiceJt.class);
        when(serviceMa.listJtMa("SITE")).thenReturn(List.of(rulesMa));
        blockerMa = new SiteBlockerMa(serviceMa, mock(ActivityServiceMa.class), 0, true);
        blockerMa.startJt();
        assertTrue(blockerMa.isRunningJt());
    }

    @BeforeEach
    void startEcho() throws IOException {
        echoMa = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        Thread tMa = new Thread(() -> {
            while (!echoMa.isClosed()) {
                try {
                    Socket sMa = echoMa.accept();
                    Thread hMa = new Thread(() -> {
                        try (sMa) {
                            InputStream inMa = sMa.getInputStream();
                            byte[] bufMa = new byte[256];
                            int nMa = inMa.read(bufMa);
                            if (nMa > 0) {
                                String reqMa = new String(bufMa, 0, nMa, StandardCharsets.ISO_8859_1);
                                OutputStream outMa = sMa.getOutputStream();
                                if (reqMa.startsWith("GET ")) {
                                    outMa.write(("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nhello")
                                            .getBytes(StandardCharsets.ISO_8859_1));
                                } else {
                                    outMa.write(("eco:" + reqMa).getBytes(StandardCharsets.ISO_8859_1));
                                }
                                outMa.flush();
                            }
                        } catch (IOException exMa) {
                            // fin de prueba
                        }
                    });
                    hMa.setDaemon(true);
                    hMa.start();
                } catch (IOException exMa) {
                    return;
                }
            }
        });
        tMa.setDaemon(true);
        tMa.start();
    }

    @AfterEach
    void stopAll() throws IOException {
        if (blockerMa != null) {
            blockerMa.stopJt();
        }
        echoMa.close();
    }

    private String firstLineAfter(String requestMa) throws IOException {
        try (Socket sMa = new Socket(InetAddress.getLoopbackAddress(), blockerMa.localPortJt())) {
            sMa.setSoTimeout(5000);
            sMa.getOutputStream().write(requestMa.getBytes(StandardCharsets.ISO_8859_1));
            sMa.getOutputStream().flush();
            return new BufferedReader(new InputStreamReader(sMa.getInputStream(), StandardCharsets.ISO_8859_1))
                    .readLine();
        }
    }

    @Test
    void facebookYSusSubdominiosSeBloquean() throws Exception {
        startWith(siteRule("facebook.com", RuleModeMa.ALWAYS, 127, 0, 0));
        for (String hostMa : List.of("facebook.com", "www.facebook.com", "m.facebook.com", "WEB.Facebook.com")) {
            assertEquals("HTTP/1.1 403 Forbidden",
                    firstLineAfter("CONNECT " + hostMa + ":443 HTTP/1.1\r\nHost: " + hostMa + ":443\r\n\r\n"),
                    "CONNECT " + hostMa);
        }
        assertEquals("HTTP/1.1 403 Forbidden",
                firstLineAfter("GET http://www.facebook.com/ HTTP/1.1\r\nHost: www.facebook.com\r\n\r\n"));
    }

    @Test
    void paginaDeBloqueoExplicaElMotivo() throws Exception {
        startWith(siteRule("facebook.com", RuleModeMa.ALWAYS, 127, 0, 0));
        try (Socket sMa = new Socket(InetAddress.getLoopbackAddress(), blockerMa.localPortJt())) {
            sMa.setSoTimeout(5000);
            sMa.getOutputStream().write("GET http://facebook.com/ HTTP/1.1\r\nHost: facebook.com\r\n\r\n"
                    .getBytes(StandardCharsets.ISO_8859_1));
            String allMa = new String(sMa.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(allMa.contains("Sitio bloqueado por AppControl"));
        }
    }

    @Test
    void sitiosParecidosOSinReglaNoSeBloquean() {
        startWith(siteRule("facebook.com", RuleModeMa.ALWAYS, 127, 0, 0));
        assertFalse(blockerMa.isBlockedJt("notfacebook.com"));
        assertFalse(blockerMa.isBlockedJt("facebook.com.otro.net"));
        assertFalse(blockerMa.isBlockedJt("google.com"));
        assertTrue(blockerMa.isBlockedJt("fr-fr.facebook.com"));
    }

    @Test
    void unSitioPermitidoPasaPorElTunelSinAlterarse() throws Exception {
        startWith(siteRule("facebook.com", RuleModeMa.ALWAYS, 127, 0, 0));
        try (Socket sMa = new Socket(InetAddress.getLoopbackAddress(), blockerMa.localPortJt())) {
            sMa.setSoTimeout(5000);
            String targetMa = "127.0.0.1:" + echoMa.getLocalPort();
            sMa.getOutputStream().write(("CONNECT " + targetMa + " HTTP/1.1\r\nHost: " + targetMa + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            sMa.getOutputStream().flush();
            BufferedReader rdMa = new BufferedReader(new InputStreamReader(sMa.getInputStream(),
                    StandardCharsets.ISO_8859_1));
            assertEquals("HTTP/1.1 200 Connection Established", rdMa.readLine());
            rdMa.readLine();   // linea en blanco
            sMa.getOutputStream().write("hola".getBytes(StandardCharsets.ISO_8859_1));
            sMa.getOutputStream().flush();
            char[] cbufMa = new char[8];
            int nMa = rdMa.read(cbufMa);
            assertEquals("eco:hola", new String(cbufMa, 0, nMa));
        }
    }

    @Test
    void unaPaginaHttpPermitidaSeReenvia() throws Exception {
        startWith(siteRule("facebook.com", RuleModeMa.ALWAYS, 127, 0, 0));
        String targetMa = "127.0.0.1:" + echoMa.getLocalPort();
        try (Socket sMa = new Socket(InetAddress.getLoopbackAddress(), blockerMa.localPortJt())) {
            sMa.setSoTimeout(5000);
            sMa.getOutputStream().write(("GET http://" + targetMa + "/x HTTP/1.1\r\nHost: " + targetMa + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            String allMa = new String(sMa.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
            assertTrue(allMa.startsWith("HTTP/1.1 200 OK"));
            assertTrue(allMa.endsWith("hello"));
        }
    }

    @Test
    void reglaPausadaODesactivadaNoBloquea() {
        BlockRuleJtMa pausedMa = siteRule("facebook.com", RuleModeMa.ALWAYS, 127, 0, 0);
        pausedMa.setStateJt(RuleStateJt.PAUSED, java.time.Instant.now().plusSeconds(600));
        startWith(pausedMa);
        assertFalse(blockerMa.isBlockedJt("www.facebook.com"));
    }

    @Test
    void elHorarioSoloBloqueaDentroDeLaFranja() {
        int nowMinMa = LocalDateTime.now().getHour() * 60 + LocalDateTime.now().getMinute();
        // franja que incluye "ahora" (de hace 1 min a dentro de 2 h, sin cruzar la medianoche si es posible)
        int startMa = Math.max(0, nowMinMa - 1);
        int endMa = Math.min(1439, nowMinMa + 120);
        BlockRuleJtMa insideMa = siteRule("facebook.com", RuleModeMa.SCHEDULE, 127, startMa, Math.max(endMa, startMa + 1));
        startWith(insideMa);
        if (nowMinMa < 1438) {
            assertTrue(blockerMa.isBlockedJt("facebook.com"));
        }
        blockerMa.stopJt();
        // franja que NO incluye "ahora" (empieza dentro de 3 h)
        int farStartMa = (nowMinMa + 180) % 1440;
        int farEndMa = (farStartMa + 30) % 1440;
        BlockRuleJtMa outsideMa = siteRule("facebook.com", RuleModeMa.SCHEDULE, 127, farStartMa, farEndMa);
        startWith(outsideMa);
        assertFalse(blockerMa.isBlockedJt("facebook.com"));
    }

    @Test
    void elScriptDeProxyEnviaSoloLosDominiosConReglaAlProxy() {
        String pacMa = SiteBlockingJtMa.buildPacJt(List.of("facebook.com", "tiktok.com"), 8081);
        assertTrue(pacMa.contains("\"facebook.com\""));
        assertTrue(pacMa.contains("\"tiktok.com\""));
        assertTrue(pacMa.contains("PROXY 127.0.0.1:8081"));
        assertTrue(pacMa.contains("return \"DIRECT\""));
        assertFalse(SiteBlockingJtMa.buildPacJt(List.of(), 8081).contains("PROXY 127.0.0.1:8081\"; }"));
    }
}
