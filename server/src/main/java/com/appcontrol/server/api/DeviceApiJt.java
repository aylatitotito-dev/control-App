package com.appcontrol.server.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.appcontrol.server.api.ApiDtoJtMa.AppDtoJt;
import com.appcontrol.server.api.ApiDtoJtMa.EventDtoMa;
import com.appcontrol.server.api.ApiDtoJtMa.RegisterInJt;
import com.appcontrol.server.api.ApiDtoJtMa.RegisterOutMa;
import com.appcontrol.server.api.ApiDtoJtMa.SyncOutMa;
import com.appcontrol.server.api.ApiDtoJtMa.UsageDtoJt;
import com.appcontrol.server.service.AuthServiceMa;
import com.appcontrol.server.service.DataServiceJtMa;
import com.appcontrol.server.service.GroupServiceJt;
import com.appcontrol.server.service.DeviceStatusServiceMa;
import com.appcontrol.server.service.ScreenServiceJt;
import com.appcontrol.server.service.ScreenServiceJt.InputEvMa;
import com.appcontrol.server.service.ScreenServiceJt.StateMa;
import org.springframework.web.bind.annotation.RequestParam;
import com.appcontrol.server.service.SyncServiceMa;

/** Lo que hace cada equipo: registrarse, consultar (sync), aceptar o terminar el control y subir sus datos. */
@RestController
@RequestMapping("/api")
public class DeviceApiJt {

    private final AuthServiceMa authJt;
    private final SyncServiceMa syncJt;
    private final GroupServiceJt groupsJt;
    private final DataServiceJtMa dataJt;
    private final ScreenServiceJt screenJt;
    private final DeviceStatusServiceMa statusJt;

    public DeviceApiJt(AuthServiceMa authJt, SyncServiceMa syncJt, GroupServiceJt groupsJt, DataServiceJtMa dataJt,
            ScreenServiceJt screenJt, DeviceStatusServiceMa statusJt) {
        this.authJt = authJt;
        this.syncJt = syncJt;
        this.groupsJt = groupsJt;
        this.dataJt = dataJt;
        this.screenJt = screenJt;
        this.statusJt = statusJt;
    }

    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterOutMa register(@RequestBody RegisterInJt inJt) {
        return authJt.register(inJt.name());
    }

    @GetMapping("/device/sync")
    public SyncOutMa sync(@RequestHeader(value = "Authorization", required = false) String headerJt) {
        return syncJt.sync(authJt.authenticate(headerJt));
    }

    @PostMapping("/device/memberships/{memberId}/accept")
    public void accept(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @PathVariable Long memberId) {
        groupsJt.respondControl(authJt.authenticate(headerJt), memberId, true);
    }

    @PostMapping("/device/memberships/{memberId}/decline")
    public void decline(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @PathVariable Long memberId) {
        groupsJt.respondControl(authJt.authenticate(headerJt), memberId, false);
    }

    @PostMapping("/device/memberships/{memberId}/end")
    public void end(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @PathVariable Long memberId) {
        groupsJt.endControl(authJt.authenticate(headerJt), memberId);
    }

    @PostMapping("/device/memberships/{memberId}/leave")
    public void leave(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @PathVariable Long memberId) {
        groupsJt.leave(authJt.authenticate(headerJt), memberId);
    }

    @PutMapping("/device/usage")
    public Map<String, Integer> usage(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestBody List<UsageDtoJt> inJt) {
        return Map.of("saved", dataJt.uploadUsage(authJt.authenticate(headerJt), inJt));
    }

    @PutMapping("/device/events")
    public Map<String, Integer> events(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestBody List<EventDtoMa> inJt) {
        return Map.of("saved", dataJt.uploadEvents(authJt.authenticate(headerJt), inJt));
    }

    @PutMapping("/device/apps")
    public Map<String, Integer> apps(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestBody List<AppDtoJt> inJt) {
        return Map.of("saved", dataJt.replaceApps(authJt.authenticate(headerJt), inJt));
    }

    /** ¿Alguien está mirando esta pantalla? Consulta muy liviana; viewer vacío = nadie. */
    @GetMapping("/device/screen")
    public Map<String, String> screenWanted(@RequestHeader(value = "Authorization", required = false) String headerJt) {
        var deviceJt = authJt.authenticate(headerJt);
        StateMa stateJt = screenJt.stateOf(deviceJt);
        return Map.of("viewer", screenJt.viewerOf(deviceJt), "inputRequested", "" + stateJt.inputRequested(),
                "inputAllowed", "" + stateJt.inputAllowed(), "full", "" + stateJt.full());
    }

    @PutMapping(value = "/device/screen", consumes = "image/jpeg")
    public Map<String, Boolean> screenFrame(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestBody byte[] jpegJt) {
        var deviceJt = authJt.authenticate(headerJt);
        boolean wantedJt = screenJt.put(deviceJt, jpegJt);
        StateMa stateJt = screenJt.stateOf(deviceJt);
        return Map.of("wanted", wantedJt, "inputRequested", stateJt.inputRequested(),
                "inputAllowed", stateJt.inputAllowed(), "full", stateJt.full());
    }

    /** El equipo cuenta cómo está (bloqueo de sitios, versión…). Solo lo ve quien lo controla. */
    @PutMapping("/device/status")
    public void putStatus(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestBody Map<String, String> bodyJt) {
        statusJt.put(authJt.authenticate(headerJt), bodyJt);
    }

    /** La persona acepta (allow=true) o corta (allow=false) el uso de su mouse y teclado. */
    @PostMapping("/device/screen/input/answer")
    public void screenInputAnswer(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestParam boolean allow) {
        screenJt.answerInput(authJt.authenticate(headerJt), allow);
    }

    /** Los gestos pendientes del dueño; se entregan una sola vez. */
    @GetMapping("/device/screen/input")
    public List<InputEvMa> screenInput(@RequestHeader(value = "Authorization", required = false) String headerJt,
            @RequestParam(defaultValue = "0") long wait) {
        return screenJt.drainInputWait(authJt.authenticate(headerJt), Math.min(wait, 2000));
    }
}
