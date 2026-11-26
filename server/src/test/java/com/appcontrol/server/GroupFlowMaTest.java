package com.appcontrol.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** El recorrido completo: codigo, aceptacion de ambos lados, control por horario y lo que ve cada quien. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:srvtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupFlowMaTest {

    /** Reloj que las pruebas pueden adelantar. */
    static final class MutableClockJt extends Clock {
        private Instant nowJt = Instant.parse("2026-11-24T15:00:00Z");

        void advanceJt(Duration byJt) {
            nowJt = nowJt.plus(byJt);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zoneJt) {
            return this;
        }

        @Override
        public Instant instant() {
            return nowJt;
        }
    }

    @TestConfiguration
    static class ClockConfigJt {
        @Bean
        @Primary
        MutableClockJt testClockMa() {
            return new MutableClockJt();
        }
    }

    @Autowired
    private MockMvc mvcMa;
    @Autowired
    private ObjectMapper jsonMa;
    @Autowired
    private MutableClockJt clockJt;

    private String ownerTokenMa;
    private String kidTokenMa;

    @BeforeEach
    void setUpJt() throws Exception {
        ownerTokenMa = register("PC de Papá");
        kidTokenMa = register("PC de Luis");
    }

    // ------------------------------------------------------------------ ayudas

    private JsonNode call(MockHttpServletRequestBuilder reqJt, String tokenJt, Object bodyJt, int statusJt)
            throws Exception {
        if (tokenJt != null) {
            reqJt.header("Authorization", "Bearer " + tokenJt);
        }
        if (bodyJt != null) {
            reqJt.contentType(MediaType.APPLICATION_JSON).content(jsonMa.writeValueAsString(bodyJt));
        }
        MvcResult resultJt = mvcMa.perform(reqJt).andReturn();
        assertEquals(statusJt, resultJt.getResponse().getStatus(), resultJt.getResponse().getContentAsString());
        String textJt = resultJt.getResponse().getContentAsString();
        return textJt.isEmpty() ? jsonMa.nullNode() : jsonMa.readTree(textJt);
    }

    private String register(String nameJt) throws Exception {
        return call(post("/api/devices"), null, java.util.Map.of("name", nameJt), 201).get("token").asText();
    }

    private JsonNode groupOf(String tokenJt) throws Exception {
        return call(get("/api/groups"), tokenJt, null, 200).get(0);
    }

    private long createGroupAndAdmit() throws Exception {
        JsonNode groupJt = call(post("/api/groups"), ownerTokenMa, java.util.Map.of("name", "Casa"), 201);
        long idJt = groupJt.get("id").asLong();
        JsonNode joinedJt = call(post("/api/groups/join"), kidTokenMa,
                java.util.Map.of("code", groupJt.get("code").asText(), "displayName", "Luis"), 200);
        assertEquals("PENDING", joinedJt.get("status").asText());
        long memberIdJt = joinedJt.get("memberId").asLong();
        call(post("/api/groups/" + idJt + "/members/" + memberIdJt + "/approve"), ownerTokenMa, null, 200);
        return idJt;
    }

    private long kidMemberId() throws Exception {
        return groupOf(kidTokenMa).get("memberId").asLong();
    }

    private java.util.Map<String, Object> timed(Duration forJt, boolean canLeaveJt) {
        return java.util.Map.of("kind", "TIMED", "until", clockJt.instant().plus(forJt).toString(),
                "memberCanLeave", canLeaveJt);
    }

    private java.util.List<java.util.Map<String, Object>> oneRule() {
        return java.util.List.of(java.util.Map.of("kind", "APP", "targetKey", "roblox", "displayName", "Roblox",
                "mode", "ALWAYS", "state", "ACTIVE", "daysMask", 127, "startMinute", 0, "endMinute", 0,
                "quotaMinutes", 0));
    }

    // ------------------------------------------------------------------ pruebas

    @Test
    void elFlujoCompletoPorHorarioYSuFinAutomatico() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;

        // El miembro no ve el codigo; el dueño si.
        assertNull(groupOf(kidTokenMa).get("code").isNull() ? null : "visible");
        assertNotNull(groupOf(ownerTokenMa).get("code").asText());

        // Aun sin aceptar el control no se pueden poner reglas ni ver nada.
        call(post(basePathJt + "/control"), ownerTokenMa, timed(Duration.ofHours(1), true), 200);
        call(put(basePathJt + "/rules"), ownerTokenMa, oneRule(), 403);
        call(get(basePathJt + "/apps"), ownerTokenMa, null, 403);
        JsonNode pendingJt = call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").get(0);
        assertEquals("REQUESTED", pendingJt.get("controlStatus").asText());
        assertEquals(0, pendingJt.get("rules").size());

        // El miembro acepta en su equipo: ahora si.
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        call(put(basePathJt + "/rules"), ownerTokenMa, oneRule(), 200);
        JsonNode activeJt = call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").get(0);
        assertEquals("ACCEPTED", activeJt.get("controlStatus").asText());
        assertEquals("roblox", activeJt.get("rules").get(0).get("targetKey").asText());

        // El equipo sube sus datos y el dueño los ve.
        String dayJt = "2026-11-24";
        call(put("/api/device/usage"), kidTokenMa, java.util.List.of(java.util.Map.of("appKey", "chrome",
                "displayName", "Chrome", "day", dayJt, "seconds", 3600)), 200);
        call(put("/api/device/apps"), kidTokenMa, java.util.List.of(java.util.Map.of("appKey", "chrome",
                "displayName", "Chrome")), 200);
        call(put("/api/device/events"), kidTokenMa, java.util.List.of(java.util.Map.of("clientId", 1, "type", "SITE",
                "targetKey", "youtube.com", "displayName", "youtube.com", "startedAt", "2026-11-24T15:10:00Z",
                "endedAt", "2026-11-24T15:20:00Z", "hits", 2)), 200);
        // Algo de antes de que aceptara el control: nunca debe verlo quien controla.
        call(put("/api/device/events"), kidTokenMa, java.util.List.of(java.util.Map.of("clientId", 2, "type", "SITE",
                "targetKey", "antes.com", "displayName", "antes.com", "startedAt", "2026-11-24T13:00:00Z",
                "endedAt", "2026-11-24T13:10:00Z", "hits", 1)), 200);
        JsonNode usageJt = call(get(basePathJt + "/usage?from=" + dayJt + "&to=" + dayJt), ownerTokenMa, null, 200);
        assertEquals(3600, usageJt.get(0).get("seconds").asLong());
        assertEquals("Chrome", call(get(basePathJt + "/apps"), ownerTokenMa, null, 200).get(0).get("displayName")
                .asText());
        assertEquals("youtube.com", call(get(basePathJt + "/events?from=2026-11-24T00:00:00Z&to=2026-11-25T00:00:00Z"),
                ownerTokenMa, null, 200).get(0).get("targetKey").asText());

        // Pasa la hora: las reglas desaparecen solas y ya no se puede ver su actividad.
        clockJt.advanceJt(Duration.ofHours(2));
        JsonNode endedJt = call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").get(0);
        assertEquals("NONE", endedJt.get("controlStatus").asText());
        assertEquals(0, endedJt.get("rules").size());
        call(get(basePathJt + "/apps"), ownerTokenMa, null, 403);

        // El control terminó, pero el historial de ESE tiempo se sigue viendo (y solo ese).
        JsonNode keptJt = call(get(basePathJt + "/events?from=2026-11-24T00:00:00Z&to=2026-11-25T00:00:00Z"),
                ownerTokenMa, null, 200);
        assertEquals(1, keptJt.size(), "solo lo ocurrido durante el control");
        assertEquals("youtube.com", keptJt.get(0).get("targetKey").asText());
        JsonNode sessionsJt = call(get(basePathJt + "/sessions"), ownerTokenMa, null, 200);
        assertEquals(1, sessionsJt.size());
        assertFalse(sessionsJt.get(0).get("endedAt").isNull(), "el tramo quedó cerrado");
        assertTrue(call(get("/api/groups/" + groupIdJt + "/members"), ownerTokenMa, null, 200).get(1).get("hasHistory")
                .asBoolean());
        // Quien no es dueño del grupo no ve nada.
        call(get(basePathJt + "/events?from=2026-11-24T00:00:00Z&to=2026-11-25T00:00:00Z"), kidTokenMa, null, 403);
    }

    @Test
    void sinTokenOConTokenFalsoNoEntra() throws Exception {
        call(get("/api/groups"), null, null, 401);
        call(get("/api/device/sync"), "no-es-un-token", null, 401);
        String fakeJt = ownerTokenMa.substring(0, ownerTokenMa.indexOf('.')) + ".secretoEquivocado";
        call(get("/api/groups"), fakeJt, null, 401);
    }

    @Test
    void unTerceroNoPuedeAprobarNiVerNada() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String strangerJt = register("PC ajena");
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        call(post(basePathJt + "/approve"), strangerJt, null, 403);
        call(post(basePathJt + "/control"), strangerJt, timed(Duration.ofHours(1), true), 403);
        call(get(basePathJt + "/usage?from=2026-11-24&to=2026-11-24"), strangerJt, null, 403);
        call(get("/api/groups/" + groupIdJt + "/members"), strangerJt, null, 404);
        // ni siquiera el propio miembro puede pedirse control a si mismo
        call(post(basePathJt + "/control"), kidTokenMa, timed(Duration.ofHours(1), true), 403);
        // y nadie puede aceptar el control de otra persona
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), strangerJt, null, 403);
    }

    @Test
    void sinAprobarAlMiembroNoSePuedePedirControl() throws Exception {
        JsonNode groupJt = call(post("/api/groups"), ownerTokenMa, java.util.Map.of("name", "Clase"), 201);
        JsonNode joinedJt = call(post("/api/groups/join"), kidTokenMa,
                java.util.Map.of("code", groupJt.get("code").asText()), 200);
        call(post("/api/groups/" + groupJt.get("id").asLong() + "/members/" + joinedJt.get("memberId").asLong()
                + "/control"), ownerTokenMa, timed(Duration.ofHours(1), true), 400);
    }

    @Test
    void elControlPermanenteSoloLoTerminaElDuenoSiNoSePermite() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        call(post(basePathJt + "/control"), ownerTokenMa,
                java.util.Map.of("kind", "PERMANENT", "memberCanLeave", false), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        call(put(basePathJt + "/rules"), ownerTokenMa, oneRule(), 200);

        // El hijo no puede terminarlo ni salir; las reglas siguen aunque pase el tiempo.
        call(post("/api/device/memberships/" + memberIdJt + "/end"), kidTokenMa, null, 403);
        call(post("/api/device/memberships/" + memberIdJt + "/leave"), kidTokenMa, null, 403);
        clockJt.advanceJt(Duration.ofDays(30));
        assertEquals(1, call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").get(0).get("rules")
                .size());

        // El dueño si, y entonces las reglas se quitan.
        call(post("/api/device/memberships/" + memberIdJt + "/end"), ownerTokenMa, null, 200);
        assertEquals(0, call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").get(0).get("rules")
                .size());
    }

    @Test
    void siElDuenoLoPermiteElMiembroPuedeSalir() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        call(post("/api/groups/" + groupIdJt + "/members/" + memberIdJt + "/control"), ownerTokenMa,
                timed(Duration.ofHours(1), true), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        call(post("/api/device/memberships/" + memberIdJt + "/leave"), kidTokenMa, null, 200);
        assertEquals(0, call(get("/api/groups"), kidTokenMa, null, 200).size());
        assertEquals(1, call(get("/api/groups/" + groupIdJt + "/members"), ownerTokenMa, null, 200).size());
    }

    @Test
    void elMiembroPuedeRechazarElControl() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        call(post("/api/groups/" + groupIdJt + "/members/" + memberIdJt + "/control"), ownerTokenMa,
                timed(Duration.ofHours(1), true), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/decline"), kidTokenMa, null, 200);
        assertEquals("NONE", call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").get(0)
                .get("controlStatus").asText());
        call(put("/api/groups/" + groupIdJt + "/members/" + memberIdJt + "/rules"), ownerTokenMa, oneRule(), 403);
    }

    @Test
    void codigoInexistenteYLimiteDeIntentos() throws Exception {
        for (int tryJt = 0; tryJt < 10; tryJt++) {
            call(post("/api/groups/join"), kidTokenMa, java.util.Map.of("code", "AAAA-AAA" + tryJt), 404);
        }
        call(post("/api/groups/join"), kidTokenMa, java.util.Map.of("code", "AAAA-AAAA"), 429);
        // Pasados 10 minutos se puede volver a intentar.
        clockJt.advanceJt(Duration.ofMinutes(11));
        call(post("/api/groups/join"), kidTokenMa, java.util.Map.of("code", "AAAA-AAAA"), 404);
    }

    @Test
    void subirElMismoEventoDosVecesNoLoDuplica() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        call(post(basePathJt + "/control"), ownerTokenMa,
                java.util.Map.of("kind", "PERMANENT", "memberCanLeave", true), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        java.util.List<java.util.Map<String, Object>> eventJt = java.util.List.of(java.util.Map.of("clientId", 7,
                "type", "APP", "targetKey", "chrome", "displayName", "Chrome", "startedAt",
                clockJt.instant().toString(), "endedAt", clockJt.instant().plusSeconds(60).toString(), "hits", 0));
        call(put("/api/device/events"), kidTokenMa, eventJt, 200);
        call(put("/api/device/events"), kidTokenMa, eventJt, 200);
        JsonNode eventsJt = call(get(basePathJt + "/events?from=2026-11-24T00:00:00Z&to=2026-11-26T00:00:00Z"),
                ownerTokenMa, null, 200);
        assertEquals(1, eventsJt.size());
        assertTrue(eventsJt.get(0).get("targetKey").asText().equals("chrome"));
        assertFalse(eventsJt.get(0).has("url"));
    }

    @Test
    void sacarAlMiembroLoQuitaDelGrupo() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        call(delete("/api/groups/" + groupIdJt + "/members/" + memberIdJt), ownerTokenMa, null, 200);
        assertEquals(0, call(get("/api/groups"), kidTokenMa, null, 200).size());
    }

    @Test
    void laPantallaSoloPasaMientrasSeMiraYHayControl() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        byte[] jpegJt = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};

        // Sin aceptar el control no se puede mirar.
        call(post(basePathJt + "/control"), ownerTokenMa, timed(Duration.ofHours(1), true), 200);
        call(get(basePathJt + "/screen"), ownerTokenMa, null, 403);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);

        // Nadie mira: el equipo no debe subir nada y la nube no lo guarda.
        assertEquals("", call(get("/api/device/screen"), kidTokenMa, null, 200).get("viewer").asText());
        assertFalse(putFrameJt(jpegJt));

        // El dueño empieza a mirar (aún sin imagen) y el equipo se entera.
        assertEquals(204, screenStatusJt(basePathJt, 0));
        assertTrue(call(get("/api/device/screen"), kidTokenMa, null, 200).get("viewer").asText().startsWith("PC de Pap"));
        assertTrue(putFrameJt(jpegJt));
        MvcResult frameJt = mvcMa.perform(get(basePathJt + "/screen?since=0").header("Authorization",
                "Bearer " + ownerTokenMa)).andReturn();
        assertEquals(200, frameJt.getResponse().getStatus());
        assertEquals(jpegJt.length, frameJt.getResponse().getContentAsByteArray().length);
        long numberJt = Long.parseLong(frameJt.getResponse().getHeader("X-Frame-No"));
        assertEquals(204, screenStatusJt(basePathJt, numberJt));

        // Si el dueño deja de preguntar, a los pocos segundos ya nadie mira y la imagen se descarta.
        clockJt.advanceJt(Duration.ofSeconds(30));
        assertEquals("", call(get("/api/device/screen"), kidTokenMa, null, 200).get("viewer").asText());
        assertFalse(putFrameJt(jpegJt));

        // Al terminar el control por horario ya no se puede mirar.
        clockJt.advanceJt(Duration.ofHours(2));
        call(get(basePathJt + "/screen"), ownerTokenMa, null, 403);
    }

    private boolean putFrameJt(byte[] jpegJt) throws Exception {
        MvcResult resultJt = mvcMa.perform(put("/api/device/screen").header("Authorization", "Bearer " + kidTokenMa)
                .contentType(MediaType.IMAGE_JPEG).content(jpegJt)).andReturn();
        assertEquals(200, resultJt.getResponse().getStatus(), resultJt.getResponse().getContentAsString());
        return jsonMa.readTree(resultJt.getResponse().getContentAsString()).get("wanted").asBoolean();
    }

    private int screenStatusJt(String basePathJt, long sinceJt) throws Exception {
        return mvcMa.perform(get(basePathJt + "/screen?since=" + sinceJt).header("Authorization",
                "Bearer " + ownerTokenMa)).andReturn().getResponse().getStatus();
    }

    @Test
    void elMouseYTecladoSeCortanConDetener() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        java.util.List<java.util.Map<String, Object>> eventsJt = java.util.List.of(
                java.util.Map.of("type", "move", "x", 0.5, "y", 0.25, "button", 0, "code", 0, "delta", 0),
                java.util.Map.of("type", "keydown", "x", 0, "y", 0, "button", 0, "code", 65, "delta", 0),
                java.util.Map.of("type", "borrar-todo", "x", 0, "y", 0, "button", 0, "code", 0, "delta", 0));

        call(post(basePathJt + "/control"), ownerTokenMa, timed(Duration.ofHours(1), false), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        assertEquals(204, screenStatusJt(basePathJt, 0));

        // Sin pedirlo antes no pasa nada.
        call(post(basePathJt + "/screen/input"), ownerTokenMa, eventsJt, 403);

        // Al aceptar el control ya aceptó esto: no se le vuelve a preguntar. Pidiéndolo, queda permitido.
        call(post(basePathJt + "/screen/input/request"), ownerTokenMa, null, 200);
        assertEquals("true", call(get("/api/device/screen"), kidTokenMa, null, 200).get("inputAllowed").asText());
        assertEquals("allowed", inputHeaderJt(basePathJt));
        call(post(basePathJt + "/screen/input"), ownerTokenMa, eventsJt, 200);
        JsonNode pulledJt = call(get("/api/device/screen/input"), kidTokenMa, null, 200);
        assertEquals(2, pulledJt.size());
        assertEquals("move", pulledJt.get(0).get("type").asText());
        assertEquals(65, pulledJt.get(1).get("code").asInt());
        assertEquals(0, call(get("/api/device/screen/input"), kidTokenMa, null, 200).size());

        // La persona corta cuando quiera.
        call(post("/api/device/screen/input/answer?allow=false"), kidTokenMa, null, 200);
        call(post(basePathJt + "/screen/input"), ownerTokenMa, eventsJt, 403);
        assertEquals("none", inputHeaderJt(basePathJt));
        // Y si el dueño insiste en esta misma sesión, no se le vuelve a dar.
        call(post(basePathJt + "/screen/input/request"), ownerTokenMa, null, 200);
        assertEquals("none", inputHeaderJt(basePathJt));
        call(post(basePathJt + "/screen/input"), ownerTokenMa, eventsJt, 403);
    }

    private String inputHeaderJt(String basePathJt) throws Exception {
        return mvcMa.perform(get(basePathJt + "/screen?since=0").header("Authorization", "Bearer " + ownerTokenMa))
                .andReturn().getResponse().getHeader("X-Input");
    }

    @Test
    void laPantallaLlegaEnCuantoElEquipoLaSubeSinPreguntarCadaRato() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        byte[] jpegJt = {(byte) 0xFF, (byte) 0xD8, 9, 9, (byte) 0xFF, (byte) 0xD9};
        call(post(basePathJt + "/control"), ownerTokenMa, timed(Duration.ofHours(1), false), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        assertEquals(204, screenStatusJt(basePathJt, 0));

        // El dueño espera hasta 3 s; el equipo sube la imagen a los 300 ms: debe llegar casi enseguida.
        java.util.concurrent.ExecutorService poolJt = java.util.concurrent.Executors.newSingleThreadExecutor();
        long startJt = System.nanoTime();
        java.util.concurrent.Future<MvcResult> waitingJt = poolJt.submit(() -> mvcMa.perform(
                get(basePathJt + "/screen?since=0&wait=3000").header("Authorization", "Bearer " + ownerTokenMa))
                .andReturn());
        Thread.sleep(300);
        assertTrue(putFrameJt(jpegJt));
        MvcResult resultJt = waitingJt.get(5, java.util.concurrent.TimeUnit.SECONDS);
        long elapsedMsJt = (System.nanoTime() - startJt) / 1_000_000;
        poolJt.shutdownNow();
        assertEquals(200, resultJt.getResponse().getStatus());
        assertEquals(jpegJt.length, resultJt.getResponse().getContentAsByteArray().length);
        assertTrue(elapsedMsJt < 2000, "llegó en " + elapsedMsJt + " ms");
    }

    @Test
    void soloElDuenoPuedeEliminarElGrupoYSeVaTodo() throws Exception {
        long groupIdJt = createGroupAndAdmit();
        long memberIdJt = kidMemberId();
        String basePathJt = "/api/groups/" + groupIdJt + "/members/" + memberIdJt;
        call(post(basePathJt + "/control"), ownerTokenMa, timed(Duration.ofHours(1), false), 200);
        call(post("/api/device/memberships/" + memberIdJt + "/accept"), kidTokenMa, null, 200);
        call(put(basePathJt + "/rules"), ownerTokenMa, oneRule(), 200);

        // La otra persona no puede eliminar el grupo.
        call(delete("/api/groups/" + groupIdJt), kidTokenMa, null, 403);
        assertEquals(1, call(get("/api/groups"), ownerTokenMa, null, 200).size());

        // El dueño sí: desaparece el grupo, y la otra PC queda libre (sin reglas ni control).
        call(delete("/api/groups/" + groupIdJt), ownerTokenMa, null, 200);
        assertEquals(0, call(get("/api/groups"), ownerTokenMa, null, 200).size());
        assertEquals(0, call(get("/api/groups"), kidTokenMa, null, 200).size());
        assertEquals(0, call(get("/api/device/sync"), kidTokenMa, null, 200).get("memberships").size());
        call(delete("/api/groups/" + groupIdJt), ownerTokenMa, null, 404);
    }
}
