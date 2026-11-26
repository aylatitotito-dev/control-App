package com.appcontrol.cloud;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.appcontrol.cloud.CloudDtoJt.AppDtoMa;
import com.appcontrol.cloud.CloudDtoJt.ControlInMa;
import com.appcontrol.cloud.CloudDtoJt.EventDtoJt;
import com.appcontrol.cloud.CloudDtoJt.GroupOutMa;
import com.appcontrol.cloud.CloudDtoJt.MemberOutJt;
import com.appcontrol.cloud.CloudDtoJt.RegisterOutJt;
import com.appcontrol.cloud.CloudDtoJt.RuleDtoJt;
import com.appcontrol.cloud.CloudDtoJt.SyncOutJt;
import com.appcontrol.cloud.CloudDtoJt.UsageDtoMa;
import com.appcontrol.service.AppSettingsServiceMa;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Habla con la API de la nube por HTTPS. La app nunca se conecta a la base de datos: solo a esta API, con el token
 * propio de este equipo. Si no hay internet lanza {@link CloudExceptionMa} con offline = true y la app sigue
 * funcionando con lo que ya tiene guardado.
 */
@Component
public class CloudClientMa {

    public static final String TOKEN_KEY_JT = "cloud_token";

    /** Error al hablar con la nube. */
    public static class CloudExceptionMa extends RuntimeException {
        private final int statusJt;
        private final boolean offlineJt;

        public CloudExceptionMa(int statusJt, boolean offlineJt, String messageJt) {
            super(messageJt);
            this.statusJt = statusJt;
            this.offlineJt = offlineJt;
        }

        public int status() {
            return statusJt;
        }

        public boolean offline() {
            return offlineJt;
        }
    }

    private final HttpClient httpMa = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper jsonMa;
    private final AppSettingsServiceMa settingsMa;
    private final String baseUrlMa;

    public CloudClientMa(ObjectMapper jsonMa, AppSettingsServiceMa settingsMa,
            @Value("${appcontrol.cloud.url:https://appcontrol-api-50205.azurewebsites.net}") String baseUrlMa) {
        this.jsonMa = jsonMa;
        this.settingsMa = settingsMa;
        this.baseUrlMa = baseUrlMa.endsWith("/") ? baseUrlMa.substring(0, baseUrlMa.length() - 1) : baseUrlMa;
    }

    public boolean hasToken() {
        return !settingsMa.get(TOKEN_KEY_JT, "").isEmpty();
    }

    private <T> T call(String methodMa, String pathMa, Object bodyMa, TypeReference<T> typeMa) {
        try {
            HttpRequest.Builder reqMa = HttpRequest.newBuilder(URI.create(baseUrlMa + pathMa))
                    // El plan gratuito de Azure se duerme sin uso: la primera consulta puede tardar en despertarlo.
                    .timeout(Duration.ofSeconds(60)).header("Accept", "application/json");
            String tokenMa = settingsMa.get(TOKEN_KEY_JT, "");
            if (!tokenMa.isEmpty()) {
                reqMa.header("Authorization", "Bearer " + tokenMa);
            }
            if (bodyMa == null) {
                reqMa.method(methodMa, HttpRequest.BodyPublishers.noBody());
            } else {
                reqMa.header("Content-Type", "application/json").method(methodMa,
                        HttpRequest.BodyPublishers.ofString(jsonMa.writeValueAsString(bodyMa)));
            }
            HttpResponse<String> respMa = httpMa.send(reqMa.build(), HttpResponse.BodyHandlers.ofString());
            if (respMa.statusCode() >= 400) {
                throw new CloudExceptionMa(respMa.statusCode(), false, errorTextMa(respMa));
            }
            if (typeMa == null || respMa.body() == null || respMa.body().isBlank()) {
                return null;
            }
            return jsonMa.readValue(respMa.body(), typeMa);
        } catch (IOException exMa) {
            CloudExceptionMa offlineMa = new CloudExceptionMa(0, true,
                    "Sin conexión con la nube. Se usará lo que ya está guardado.");
            offlineMa.initCause(exMa);
            throw offlineMa;
        } catch (InterruptedException exMa) {
            Thread.currentThread().interrupt();
            throw new CloudExceptionMa(0, true, "Operación cancelada.");
        }
    }

    private String errorTextMa(HttpResponse<String> respMa) {
        try {
            Map<String, String> mapMa = jsonMa.readValue(respMa.body(), new TypeReference<Map<String, String>>() {
            });
            String textMa = mapMa.get("error");
            if (textMa != null) {
                return textMa;
            }
        } catch (IOException | RuntimeException exMa) {
            // la respuesta no era JSON: se usa un texto generico
        }
        return respMa.statusCode() == 401 ? "La nube no reconoce este equipo." : "Error de la nube (" + respMa.statusCode() + ").";
    }

    private static String enc(String textMa) {
        return URLEncoder.encode(textMa, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ equipo

    public RegisterOutJt register(String nameMa) {
        return call("POST", "/api/devices", Map.of("name", nameMa), new TypeReference<>() {
        });
    }

    public SyncOutJt sync() {
        return call("GET", "/api/device/sync", null, new TypeReference<>() {
        });
    }

    public void accept(long memberIdMa) {
        call("POST", "/api/device/memberships/" + memberIdMa + "/accept", null, null);
    }

    public void decline(long memberIdMa) {
        call("POST", "/api/device/memberships/" + memberIdMa + "/decline", null, null);
    }

    public void endControl(long memberIdMa) {
        call("POST", "/api/device/memberships/" + memberIdMa + "/end", null, null);
    }

    public void leave(long memberIdMa) {
        call("POST", "/api/device/memberships/" + memberIdMa + "/leave", null, null);
    }

    public void putUsage(List<UsageDtoMa> itemsMa) {
        call("PUT", "/api/device/usage", itemsMa, null);
    }

    public void putEvents(List<EventDtoJt> itemsMa) {
        call("PUT", "/api/device/events", itemsMa, null);
    }

    public void putApps(List<AppDtoMa> itemsMa) {
        call("PUT", "/api/device/apps", itemsMa, null);
    }

    // ------------------------------------------------------------------ grupos

    public GroupOutMa createGroup(String nameMa) {
        return call("POST", "/api/groups", Map.of("name", nameMa), new TypeReference<>() {
        });
    }

    public List<GroupOutMa> groups() {
        return call("GET", "/api/groups", null, new TypeReference<>() {
        });
    }

    public GroupOutMa join(String codeMa, String displayNameMa) {
        return call("POST", "/api/groups/join", Map.of("code", codeMa, "displayName", displayNameMa),
                new TypeReference<>() {
                });
    }

    public List<MemberOutJt> members(long groupIdMa) {
        return call("GET", "/api/groups/" + groupIdMa + "/members", null, new TypeReference<>() {
        });
    }

    public void approve(long groupIdMa, long memberIdMa) {
        call("POST", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/approve", null, null);
    }

    /** Elimina el grupo completo (solo su dueño). */
    public void deleteGroup(long groupIdMa) {
        call("DELETE", "/api/groups/" + groupIdMa, null, null);
    }

    public void remove(long groupIdMa, long memberIdMa) {
        call("DELETE", "/api/groups/" + groupIdMa + "/members/" + memberIdMa, null, null);
    }

    public void requestControl(long groupIdMa, long memberIdMa, ControlInMa controlMa) {
        call("POST", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/control", controlMa, null);
    }

    public List<RuleDtoJt> rules(long groupIdMa, long memberIdMa) {
        return call("GET", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/rules", null,
                new TypeReference<>() {
                });
    }

    public void setRules(long groupIdMa, long memberIdMa, List<RuleDtoJt> rulesMa) {
        call("PUT", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/rules", rulesMa, null);
    }

    public List<UsageDtoMa> usage(long groupIdMa, long memberIdMa, LocalDate fromMa, LocalDate toMa) {
        return call("GET", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/usage?from=" + fromMa + "&to="
                + toMa, null, new TypeReference<>() {
                });
    }

    public List<EventDtoJt> events(long groupIdMa, long memberIdMa, Instant fromMa, Instant toMa) {
        return call("GET", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/events?from="
                + enc(fromMa.toString()) + "&to=" + enc(toMa.toString()), null, new TypeReference<>() {
                });
    }

    /** Los tramos en que ese equipo estuvo bajo control (el historial de esos tramos se puede ver aunque ya terminaron). */
    public List<CloudDtoJt.SessionDtoJt> sessions(long groupIdMa, long memberIdMa) {
        return call("GET", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/sessions", null,
                new TypeReference<>() {
                });
    }

    public List<AppDtoMa> apps(long groupIdMa, long memberIdMa) {
        return call("GET", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/apps", null,
                new TypeReference<>() {
                });
    }

    /** Este equipo cuenta cómo está (bloqueo de sitios, versión). Solo lo ve quien lo controla. */
    public void putStatus(Map<String, String> statusMa) {
        call("PUT", "/api/device/status", statusMa, null);
    }

    /** Dueño: cómo está ese equipo según él mismo. Vacío si todavía no ha informado. */
    public Map<String, String> memberStatus(long groupIdMa, long memberIdMa) {
        Map<String, String> mapMa = call("GET", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/status", null,
                new TypeReference<>() {
                });
        return mapMa == null ? Map.of() : mapMa;
    }

    // ------------------------------------------------------------------ pantalla en vivo (imagenes JPEG)

    /** Una imagen de pantalla recibida y su numero. */
    public record ScreenFrameMa(byte[] jpeg, long number, String input, boolean applying) {
    }

    /** Un gesto: type = move, down, up, wheel, keydown o keyup. x e y van de 0 a 1 (fracción de la pantalla). */
    public record InputEvMa(String type, double x, double y, int button, int code, int delta) {
    }

    /** Lo que el equipo controlado se entera al subir una imagen. */
    public record ScreenStateMa(boolean wanted, boolean inputRequested, boolean inputAllowed, boolean full) {
    }

    private HttpRequest.Builder rawRequestMa(String pathMa) {
        HttpRequest.Builder reqMa = HttpRequest.newBuilder(URI.create(baseUrlMa + pathMa))
                .timeout(Duration.ofSeconds(20));
        String tokenMa = settingsMa.get(TOKEN_KEY_JT, "");
        if (!tokenMa.isEmpty()) {
            reqMa.header("Authorization", "Bearer " + tokenMa);
        }
        return reqMa;
    }

    private <T> HttpResponse<T> sendRawMa(HttpRequest reqMa, HttpResponse.BodyHandler<T> handlerMa) {
        try {
            HttpResponse<T> respMa = httpMa.send(reqMa, handlerMa);
            if (respMa.statusCode() >= 400) {
                String bodyMa = respMa.body() instanceof String textMa ? textMa : "";
                throw new CloudExceptionMa(respMa.statusCode(), false, respMa.statusCode() == 403
                        ? "Esa persona no ha aceptado el control (o ya terminó)."
                        : bodyMa.isBlank() ? "Error de la nube (" + respMa.statusCode() + ")." : bodyMa);
            }
            return respMa;
        } catch (IOException exMa) {
            CloudExceptionMa offlineMa = new CloudExceptionMa(0, true, "Sin conexión con la nube.");
            offlineMa.initCause(exMa);
            throw offlineMa;
        } catch (InterruptedException exMa) {
            Thread.currentThread().interrupt();
            throw new CloudExceptionMa(0, true, "Operación cancelada.");
        }
    }

    /** Equipo controlado: ¿alguien mira mi pantalla? Devuelve el nombre de quien mira, o vacío. */
    public String screenViewer() {
        Map<String, String> mapMa = call("GET", "/api/device/screen", null, new TypeReference<>() {
        });
        return mapMa == null ? "" : mapMa.getOrDefault("viewer", "");
    }

    /** Equipo controlado: sube una imagen y se entera si todavía la piden y si hay pedido o permiso de mouse/teclado. */
    public ScreenStateMa putScreen(byte[] jpegMa) {
        HttpRequest reqMa = rawRequestMa("/api/device/screen").header("Content-Type", "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(jpegMa)).build();
        String bodyMa = sendRawMa(reqMa, HttpResponse.BodyHandlers.ofString()).body();
        try {
            Map<String, Boolean> mapMa = jsonMa.readValue(bodyMa, new TypeReference<Map<String, Boolean>>() {
            });
            return new ScreenStateMa(Boolean.TRUE.equals(mapMa.get("wanted")),
                    Boolean.TRUE.equals(mapMa.get("inputRequested")), Boolean.TRUE.equals(mapMa.get("inputAllowed")),
                    Boolean.TRUE.equals(mapMa.get("full")));
        } catch (IOException exMa) {
            return new ScreenStateMa(false, false, false, false);
        }
    }

    /** Equipo controlado: la persona permite (true) o corta (false) el uso de su mouse y teclado. */
    public void answerScreenInput(boolean allowMa) {
        call("POST", "/api/device/screen/input/answer?allow=" + allowMa, null, null);
    }

    /** Equipo controlado: los gestos pendientes del dueño. */
    public List<InputEvMa> pullScreenInput(long waitMsMa) {
        List<InputEvMa> listMa = call("GET", "/api/device/screen/input?wait=" + waitMsMa, null, new TypeReference<>() {
        });
        return listMa == null ? List.of() : listMa;
    }

    /** Dueño: pide a la persona usar su mouse y teclado. */
    public void requestScreenInput(long groupIdMa, long memberIdMa) {
        call("POST", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/screen/input/request", null, null);
    }

    /** Dueño: envía gestos (solo funciona si la persona lo permitió). */
    public void sendScreenInput(long groupIdMa, long memberIdMa, List<InputEvMa> eventsMa) {
        call("POST", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/screen/input", eventsMa, null);
    }

    /** Dueño: deja de usar el mouse y teclado (sigue viendo). */
    public void stopScreenInput(long groupIdMa, long memberIdMa) {
        call("DELETE", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/screen/input", null, null);
    }

    /** Dueño: pide la imagen más nueva (jpeg null si no hay una distinta). También mantiene la sesión viva y dice el estado del mouse/teclado. */
    public ScreenFrameMa watchScreen(long groupIdMa, long memberIdMa, long sinceMa, boolean fullMa, long waitMsMa) {
        HttpRequest reqMa = rawRequestMa("/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/screen?since="
                + sinceMa + "&full=" + fullMa + "&wait=" + waitMsMa).GET().build();
        HttpResponse<byte[]> respMa = sendRawMa(reqMa, HttpResponse.BodyHandlers.ofByteArray());
        String inputMa = respMa.headers().firstValue("X-Input").orElse("none");
        boolean applyingMa = "true".equals(respMa.headers().firstValue("X-Applying").orElse("false"));
        if (respMa.statusCode() == 204 || respMa.body().length == 0) {
            return new ScreenFrameMa(null, 0, inputMa, applyingMa);
        }
        long numberMa = Long.parseLong(respMa.headers().firstValue("X-Frame-No").orElse("0"));
        return new ScreenFrameMa(respMa.body(), numberMa, inputMa, applyingMa);
    }

    public void stopScreen(long groupIdMa, long memberIdMa) {
        call("DELETE", "/api/groups/" + groupIdMa + "/members/" + memberIdMa + "/screen", null, null);
    }

    /** Guarda el token que entrega la nube al registrar este equipo. */
    public void saveToken(String tokenMa) {
        settingsMa.set(TOKEN_KEY_JT, tokenMa);
    }
}
