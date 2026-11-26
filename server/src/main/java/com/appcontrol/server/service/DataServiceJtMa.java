package com.appcontrol.server.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.server.api.ApiDtoJtMa.AppDtoJt;
import com.appcontrol.server.api.ApiDtoJtMa.EventDtoMa;
import com.appcontrol.server.api.ApiDtoJtMa.UsageDtoJt;
import com.appcontrol.server.api.ApiExceptionMa;
import com.appcontrol.server.api.ApiDtoJtMa.SessionDtoMa;
import com.appcontrol.server.model.ControlSessionMa;
import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.model.EventRowJtMa;
import com.appcontrol.server.model.InstalledRowMa;
import com.appcontrol.server.model.MemberJt;
import com.appcontrol.server.model.UsageRowJt;
import com.appcontrol.server.repo.ReposJtMa.AppRepoJt;
import com.appcontrol.server.repo.ReposJtMa.EventRepoMa;
import com.appcontrol.server.repo.ReposJtMa.UsageRepoJt;

/**
 * Datos que sube cada equipo (uso por programa, historial, programas instalados) y que el dueño puede ver
 * solo si esa persona acepto el control y este sigue vigente. Solo programa o dominio + hora, nunca URL.
 */
@Service
public class DataServiceJtMa {

    private static final int MAX_ITEMS_JT = 1000;

    private final UsageRepoJt usageJt;
    private final EventRepoMa eventsJt;
    private final AppRepoJt appsJt;
    private final GroupServiceJt groupsJt;
    private final com.appcontrol.server.repo.ReposJtMa.MemberRepoJt membersJt;
    private final Clock clockJt;

    public DataServiceJtMa(UsageRepoJt usageJt, EventRepoMa eventsJt, AppRepoJt appsJt, GroupServiceJt groupsJt,
            com.appcontrol.server.repo.ReposJtMa.MemberRepoJt membersJt, Clock clockJt) {
        this.usageJt = usageJt;
        this.eventsJt = eventsJt;
        this.appsJt = appsJt;
        this.groupsJt = groupsJt;
        this.membersJt = membersJt;
        this.clockJt = clockJt;
    }

    // ------------------------------------------------------------------ subida (lo hace el equipo)

    @Transactional
    public int uploadUsage(DeviceJtMa deviceJt, List<UsageDtoJt> itemsJt) {
        checkSizeJt(itemsJt);
        int countJt = 0;
        for (UsageDtoJt itemJt : itemsJt) {
            if (itemJt.appKey() == null || itemJt.appKey().isBlank() || itemJt.day() == null || itemJt.seconds() < 0) {
                continue;
            }
            String keyJt = cut(itemJt.appKey());
            String nameJt = itemJt.displayName() == null ? keyJt : cut(itemJt.displayName());
            UsageRowJt rowJt = usageJt.findByDeviceIdAndAppKeyAndDay(deviceJt.getId(), keyJt, itemJt.day())
                    .orElseGet(() -> new UsageRowJt(deviceJt.getId(), keyJt, nameJt, itemJt.day(), 0));
            rowJt.update(nameJt, itemJt.seconds());
            usageJt.save(rowJt);
            countJt++;
        }
        return countJt;
    }

    @Transactional
    public int uploadEvents(DeviceJtMa deviceJt, List<EventDtoMa> itemsJt) {
        checkSizeJt(itemsJt);
        int countJt = 0;
        for (EventDtoMa itemJt : itemsJt) {
            if (itemJt.type() == null || itemJt.targetKey() == null || itemJt.startedAt() == null) {
                continue;
            }
            EventRowJtMa rowJt = eventsJt.findByDeviceIdAndClientId(deviceJt.getId(), itemJt.clientId())
                    .orElseGet(() -> new EventRowJtMa(deviceJt.getId(), itemJt.clientId()));
            Instant endJt = itemJt.endedAt() == null ? itemJt.startedAt() : itemJt.endedAt();
            rowJt.update(itemJt.type(), cut(itemJt.targetKey()),
                    itemJt.displayName() == null ? cut(itemJt.targetKey()) : cut(itemJt.displayName()),
                    itemJt.startedAt(), endJt, Math.max(0, itemJt.hits()));
            eventsJt.save(rowJt);
            countJt++;
        }
        return countJt;
    }

    @Transactional
    public int replaceApps(DeviceJtMa deviceJt, List<AppDtoJt> itemsJt) {
        checkSizeJt(itemsJt);
        appsJt.deleteByDeviceId(deviceJt.getId());
        appsJt.flush();
        int countJt = 0;
        for (AppDtoJt itemJt : itemsJt) {
            if (itemJt.appKey() != null && !itemJt.appKey().isBlank()) {
                appsJt.save(new InstalledRowMa(deviceJt.getId(), cut(itemJt.appKey()),
                        itemJt.displayName() == null ? cut(itemJt.appKey()) : cut(itemJt.displayName())));
                countJt++;
            }
        }
        return countJt;
    }

    // ------------------------------------------------------------------ consulta (lo hace el dueño)

    @Transactional
    public List<UsageDtoJt> viewUsage(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, LocalDate fromJt,
            LocalDate toJt) {
        MemberJt memberJt = visibleMemberJt(ownerJt, groupIdJt, memberIdJt);
        return usageJt.findByDeviceIdAndDayBetweenOrderByDayAsc(memberJt.getDeviceId(), fromJt, toJt).stream()
                .map(rowJt -> new UsageDtoJt(rowJt.getAppKey(), rowJt.getDisplayName(), rowJt.getDay(),
                        rowJt.getSeconds()))
                .toList();
    }

    @Transactional
    public List<EventDtoMa> viewEvents(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, Instant fromJt,
            Instant toJt) {
        MemberJt memberJt = groupsJt.memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        Instant nowJt = Instant.now(clockJt);
        groupsJt.expireIfNeededJt(memberJt, nowJt);
        groupsJt.ensureSessionJt(memberJt, nowJt);
        List<ControlSessionMa> sessionsListJt = groupsJt.sessionsOfJt(memberJt.getId());
        if (sessionsListJt.isEmpty()) {
            throw ApiExceptionMa.forbidden("Todavía no hubo control sobre ese equipo.");
        }
        return eventsJt.findByDeviceIdAndStartedAtBetweenOrderByStartedAtDesc(memberJt.getDeviceId(), fromJt, toJt)
                .stream().filter(rowJt -> insideSessionJt(rowJt, sessionsListJt, nowJt))
                .map(rowJt -> new EventDtoMa(rowJt.getClientId(), rowJt.getType(), rowJt.getTargetKey(),
                        rowJt.getDisplayName(), rowJt.getStartedAt(), rowJt.getEndedAt(), rowJt.getHits()))
                .toList();
    }

    /** Los tramos en que ese equipo estuvo bajo control (para elegir el periodo del historial). */
    @Transactional
    public List<SessionDtoMa> viewSessions(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = groupsJt.memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        Instant nowJt = Instant.now(clockJt);
        groupsJt.expireIfNeededJt(memberJt, nowJt);
        groupsJt.ensureSessionJt(memberJt, nowJt);
        return groupsJt.sessionsOfJt(memberJt.getId()).stream()
                .map(sessionJt -> new SessionDtoMa(sessionJt.getStartedAt(), sessionJt.getEndedAt())).toList();
    }

    /** Solo cuenta lo que ocurrió mientras había control: nada de antes ni de después. */
    private static boolean insideSessionJt(EventRowJtMa rowJt, List<ControlSessionMa> sessionsJt, Instant nowJt) {
        for (ControlSessionMa sessionJt : sessionsJt) {
            Instant endJt = sessionJt.getEndedAt() == null ? nowJt.plusSeconds(60) : sessionJt.getEndedAt();
            if (rowJt.getStartedAt().isBefore(endJt) && rowJt.getEndedAt().isAfter(sessionJt.getStartedAt())) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public List<AppDtoJt> viewApps(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = visibleMemberJt(ownerJt, groupIdJt, memberIdJt);
        return appsJt.findByDeviceIdOrderByDisplayNameAsc(memberJt.getDeviceId()).stream()
                .map(rowJt -> new AppDtoJt(rowJt.getAppKey(), rowJt.getDisplayName())).toList();
    }

    /** Solo el dueño del grupo, y solo si esa persona acepto el control y sigue vigente. */
    private MemberJt visibleMemberJt(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = groupsJt.memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        Instant nowJt = Instant.now(clockJt);
        groupsJt.expireIfNeededJt(memberJt, nowJt);
        if (!memberJt.controlActive(nowJt)) {
            throw ApiExceptionMa.forbidden("Esa persona no ha aceptado el control (o ya terminó).");
        }
        return memberJt;
    }

    private static void checkSizeJt(List<?> itemsJt) {
        if (itemsJt == null || itemsJt.size() > MAX_ITEMS_JT) {
            throw ApiExceptionMa.badRequest("Envía entre 0 y " + MAX_ITEMS_JT + " elementos por vez.");
        }
    }

    private static String cut(String textJt) {
        return textJt.length() > 255 ? textJt.substring(0, 255) : textJt;
    }
}
