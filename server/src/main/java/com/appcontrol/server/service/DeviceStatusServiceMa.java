package com.appcontrol.server.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.model.MemberJt;

/**
 * Lo que cada equipo cuenta de sí mismo (por ejemplo: "el bloqueo de sitios está activo" o "no se pudo activar porque
 * ya hay otro proxy"). Solo en memoria y solo mientras hay control vigente: sirve para que quien controla sepa si sus
 * reglas se están cumpliendo de verdad. No incluye nada de lo que hace la persona.
 */
@Service
public class DeviceStatusServiceMa {

    private record EntryMa(Map<String, String> values, Instant at) {
    }

    private final Map<Long, EntryMa> entriesMa = new ConcurrentHashMap<>();
    private final GroupServiceJt groupsMa;
    private final Clock clockMa;

    public DeviceStatusServiceMa(GroupServiceJt groupsMa, Clock clockMa) {
        this.groupsMa = groupsMa;
        this.clockMa = clockMa;
    }

    public void put(DeviceJtMa deviceMa, Map<String, String> valuesMa) {
        Map<String, String> cleanMa = new LinkedHashMap<>();
        if (valuesMa != null) {
            valuesMa.entrySet().stream().limit(12).forEach(entryMa -> {
                if (entryMa.getKey() != null && entryMa.getValue() != null) {
                    cleanMa.put(cut(entryMa.getKey(), 40), cut(entryMa.getValue(), 80));
                }
            });
        }
        entriesMa.put(deviceMa.getId(), new EntryMa(cleanMa, Instant.now(clockMa)));
    }

    /** Solo quien controla ese equipo con el control vigente puede verlo. */
    public Map<String, String> of(DeviceJtMa ownerMa, Long groupIdMa, Long memberIdMa) {
        MemberJt memberMa = groupsMa.memberOfOwnerJt(ownerMa, groupIdMa, memberIdMa);
        groupsMa.expireIfNeededJt(memberMa, Instant.now(clockMa));
        EntryMa entryMa = entriesMa.get(memberMa.getDeviceId());
        Map<String, String> outMa = new LinkedHashMap<>();
        if (entryMa != null) {
            outMa.putAll(entryMa.values());
            outMa.put("secondsAgo", "" + Math.max(0, Instant.now(clockMa).getEpochSecond() - entryMa.at().getEpochSecond()));
        }
        return outMa;
    }

    private static String cut(String textMa, int maxMa) {
        return textMa.length() > maxMa ? textMa.substring(0, maxMa) : textMa;
    }
}
