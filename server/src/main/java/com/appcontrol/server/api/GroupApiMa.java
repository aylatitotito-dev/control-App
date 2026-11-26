package com.appcontrol.server.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.appcontrol.server.api.ApiDtoJtMa.AppDtoJt;
import com.appcontrol.server.api.ApiDtoJtMa.ControlInJt;
import com.appcontrol.server.api.ApiDtoJtMa.EventDtoMa;
import com.appcontrol.server.api.ApiDtoJtMa.GroupInJt;
import com.appcontrol.server.api.ApiDtoJtMa.GroupOutJt;
import com.appcontrol.server.api.ApiDtoJtMa.JoinInMa;
import com.appcontrol.server.api.ApiDtoJtMa.MemberOutMa;
import com.appcontrol.server.api.ApiDtoJtMa.RuleDtoMa;
import com.appcontrol.server.api.ApiDtoJtMa.UsageDtoJt;
import com.appcontrol.server.service.AuthServiceMa;
import com.appcontrol.server.service.DataServiceJtMa;
import com.appcontrol.server.service.GroupServiceJt;
import com.appcontrol.server.service.DeviceStatusServiceMa;
import com.appcontrol.server.service.ScreenServiceJt;
import com.appcontrol.server.service.ScreenServiceJt.FrameMa;
import org.springframework.http.MediaType;
import com.appcontrol.server.service.ScreenServiceJt.InputEvMa;
import org.springframework.http.ResponseEntity;

/** Grupos, union, control y vista del equipo de un miembro. */
@RestController
@RequestMapping("/api")
public class GroupApiMa {

    private final AuthServiceMa authMa;
    private final GroupServiceJt groupsMa;
    private final DataServiceJtMa dataMa;
    private final ScreenServiceJt screenMa;
    private final DeviceStatusServiceMa statusMa;

    public GroupApiMa(AuthServiceMa authMa, GroupServiceJt groupsMa, DataServiceJtMa dataMa,
            ScreenServiceJt screenMa, DeviceStatusServiceMa statusMa) {
        this.authMa = authMa;
        this.groupsMa = groupsMa;
        this.dataMa = dataMa;
        this.screenMa = screenMa;
        this.statusMa = statusMa;
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupOutJt create(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @RequestBody GroupInJt inMa) {
        return groupsMa.create(authMa.authenticate(authMa2), inMa.name());
    }

    @DeleteMapping("/groups/{groupId}")
    public void deleteGroup(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId) {
        groupsMa.deleteGroup(authMa.authenticate(authMa2), groupId);
    }

    @GetMapping("/groups")
    public List<GroupOutJt> list(@RequestHeader(value = "Authorization", required = false) String authMa2) {
        return groupsMa.list(authMa.authenticate(authMa2));
    }

    @PostMapping("/groups/join")
    public GroupOutJt join(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @RequestBody JoinInMa inMa) {
        return groupsMa.join(authMa.authenticate(authMa2), inMa.code(), inMa.displayName());
    }

    @GetMapping("/groups/{groupId}/members")
    public List<MemberOutMa> members(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId) {
        return groupsMa.members(authMa.authenticate(authMa2), groupId);
    }

    @PostMapping("/groups/{groupId}/members/{memberId}/approve")
    public void approve(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        groupsMa.approve(authMa.authenticate(authMa2), groupId, memberId);
    }

    @DeleteMapping("/groups/{groupId}/members/{memberId}")
    public void remove(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        groupsMa.remove(authMa.authenticate(authMa2), groupId, memberId);
    }

    @PostMapping("/groups/{groupId}/members/{memberId}/control")
    public void requestControl(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId, @RequestBody ControlInJt inMa) {
        groupsMa.requestControl(authMa.authenticate(authMa2), groupId, memberId, inMa);
    }

    @GetMapping("/groups/{groupId}/members/{memberId}/rules")
    public List<RuleDtoMa> rules(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        return groupsMa.rulesOf(authMa.authenticate(authMa2), groupId, memberId);
    }

    @PutMapping("/groups/{groupId}/members/{memberId}/rules")
    public void setRules(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId, @RequestBody List<RuleDtoMa> inMa) {
        groupsMa.setRules(authMa.authenticate(authMa2), groupId, memberId, inMa);
    }

    // --- lo que ve el dueño del equipo de un miembro: Aplicaciones, Sitios y Historial

    @GetMapping("/groups/{groupId}/members/{memberId}/usage")
    public List<UsageDtoJt> usage(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId, @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return dataMa.viewUsage(authMa.authenticate(authMa2), groupId, memberId, from, to);
    }

    @GetMapping("/groups/{groupId}/members/{memberId}/events")
    public List<EventDtoMa> events(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId, @RequestParam Instant from,
            @RequestParam Instant to) {
        return dataMa.viewEvents(authMa.authenticate(authMa2), groupId, memberId, from, to);
    }

    @GetMapping("/groups/{groupId}/members/{memberId}/sessions")
    public List<ApiDtoJtMa.SessionDtoMa> sessions(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        return dataMa.viewSessions(authMa.authenticate(authMa2), groupId, memberId);
    }

    @GetMapping("/groups/{groupId}/members/{memberId}/apps")
    public List<AppDtoJt> apps(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        return dataMa.viewApps(authMa.authenticate(authMa2), groupId, memberId);
    }

    // --- pantalla en vivo (solo en memoria, mientras el dueño la mira)

    @GetMapping("/groups/{groupId}/members/{memberId}/screen")
    public ResponseEntity<byte[]> screen(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId,
            @RequestParam(defaultValue = "0") long since, @RequestParam(defaultValue = "false") boolean full,
            @RequestParam(defaultValue = "0") long wait) {
        var deviceMa = authMa.authenticate(authMa2);
        FrameMa frameMa = screenMa.watchWait(deviceMa, groupId, memberId, since, full, Math.min(wait, 3000));
        String inputMa = screenMa.inputState(deviceMa, groupId, memberId);
        String applyingMa = "" + screenMa.inputApplying(deviceMa, groupId, memberId);
        if (frameMa == null) {
            return ResponseEntity.noContent().header("X-Input", inputMa).header("X-Applying", applyingMa).build();
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).header("X-Frame-No", "" + frameMa.number())
                .header("X-Input", inputMa).header("X-Applying", applyingMa).body(frameMa.jpeg());
    }

    @GetMapping("/groups/{groupId}/members/{memberId}/status")
    public java.util.Map<String, String> status(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        return statusMa.of(authMa.authenticate(authMa2), groupId, memberId);
    }

    @PostMapping("/groups/{groupId}/members/{memberId}/screen/input/request")
    public void requestInput(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        screenMa.requestInput(authMa.authenticate(authMa2), groupId, memberId);
    }

    @PostMapping("/groups/{groupId}/members/{memberId}/screen/input")
    public void sendInput(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId, @RequestBody List<InputEvMa> inMa) {
        screenMa.sendInput(authMa.authenticate(authMa2), groupId, memberId, inMa);
    }

    @DeleteMapping("/groups/{groupId}/members/{memberId}/screen/input")
    public void stopInput(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        screenMa.stopInput(authMa.authenticate(authMa2), groupId, memberId);
    }

    @DeleteMapping("/groups/{groupId}/members/{memberId}/screen")
    public void stopScreen(@RequestHeader(value = "Authorization", required = false) String authMa2,
            @PathVariable Long groupId, @PathVariable Long memberId) {
        screenMa.stop(authMa.authenticate(authMa2), groupId, memberId);
    }
}
