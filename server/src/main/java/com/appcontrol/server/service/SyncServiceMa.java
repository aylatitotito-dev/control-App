package com.appcontrol.server.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.server.api.ApiDtoJtMa.RuleDtoMa;
import com.appcontrol.server.api.ApiDtoJtMa.SyncMembershipJt;
import com.appcontrol.server.api.ApiDtoJtMa.SyncOutMa;
import com.appcontrol.server.model.CloudGroupMa;
import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.model.MemberJt;
import com.appcontrol.server.repo.ReposJtMa.DeviceRepoJt;
import com.appcontrol.server.repo.ReposJtMa.GroupRepoMa;
import com.appcontrol.server.repo.ReposJtMa.MemberRepoJt;

/**
 * Lo que un equipo recibe al consultar: sus membresias, las solicitudes de control pendientes y las reglas
 * vigentes. El equipo las guarda en su base local y las aplica solo; si no hay internet, sigue con las ultimas.
 */
@Service
public class SyncServiceMa {

    private final MemberRepoJt membersMa;
    private final GroupRepoMa groupsMa;
    private final DeviceRepoJt devicesMa;
    private final GroupServiceJt groupServiceMa;
    private final Clock clockMa;

    public SyncServiceMa(MemberRepoJt membersMa, GroupRepoMa groupsMa, DeviceRepoJt devicesMa,
            GroupServiceJt groupServiceMa, Clock clockMa) {
        this.membersMa = membersMa;
        this.groupsMa = groupsMa;
        this.devicesMa = devicesMa;
        this.groupServiceMa = groupServiceMa;
        this.clockMa = clockMa;
    }

    @Transactional
    public SyncOutMa sync(DeviceJtMa deviceMa) {
        Instant nowMa = Instant.now(clockMa);
        List<SyncMembershipJt> outMa = new ArrayList<>();
        for (MemberJt memberMa : membersMa.findByDeviceId(deviceMa.getId())) {
            CloudGroupMa groupMa = groupsMa.findById(memberMa.getGroupId()).orElse(null);
            if (groupMa == null) {
                continue;
            }
            groupServiceMa.expireIfNeededJt(memberMa, nowMa);
            groupServiceMa.ensureSessionJt(memberMa, nowMa);
            String ownerNameMa = devicesMa.findById(groupMa.getOwnerDeviceId()).map(DeviceJtMa::getName).orElse("");
            // Las reglas solo viajan si el control esta aceptado y dentro de su horario.
            List<RuleDtoMa> rulesMa = memberMa.controlActive(nowMa) && MemberJt.MEMBER.equals(memberMa.getRole())
                    ? groupServiceMa.toDtosJt(memberMa) : List.of();
            outMa.add(new SyncMembershipJt(memberMa.getId(), groupMa.getId(), groupMa.getName(), ownerNameMa,
                    memberMa.getRole(), memberMa.getStatus(), memberMa.getControlStatus(), memberMa.getControlKind(),
                    memberMa.getControlFrom(), memberMa.getControlUntil(), memberMa.isMemberCanLeave(),
                    memberMa.controlActive(nowMa) ? groupServiceMa.openSessionStartJt(memberMa.getId()) : null, rulesMa));
        }
        return new SyncOutMa(nowMa, outMa);
    }
}
