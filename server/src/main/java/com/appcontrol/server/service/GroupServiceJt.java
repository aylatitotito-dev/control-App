package com.appcontrol.server.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.server.api.ApiDtoJtMa.ControlInJt;
import com.appcontrol.server.api.ApiDtoJtMa.GroupOutJt;
import com.appcontrol.server.api.ApiDtoJtMa.MemberOutMa;
import com.appcontrol.server.api.ApiDtoJtMa.RuleDtoMa;
import com.appcontrol.server.api.ApiExceptionMa;
import com.appcontrol.server.model.CloudGroupMa;
import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.model.MemberJt;
import com.appcontrol.server.model.RemoteRuleMa;
import com.appcontrol.server.repo.ReposJtMa.DeviceRepoJt;
import com.appcontrol.server.repo.ReposJtMa.GroupRepoMa;
import com.appcontrol.server.repo.ReposJtMa.MemberRepoJt;
import com.appcontrol.server.repo.ReposJtMa.RuleRepoMa;
import com.appcontrol.server.repo.ReposJtMa.EventRepoMa;
import com.appcontrol.server.repo.ReposJtMa.SessionRepoMa;
import com.appcontrol.server.model.ControlSessionMa;

/**
 * Grupos, union con codigo y control en dos pasos: (1) la persona se une con el codigo y el dueño la acepta;
 * (2) el dueño solicita control (permanente o por horario) y la persona lo acepta en su equipo.
 */
@Service
public class GroupServiceJt {

    /** Sin caracteres ambiguos (0/O, 1/I). */
    private static final String ALPHABET_JT = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM_JT = new SecureRandom();
    private static final int MAX_FAILED_CODES_JT = 10;
    private static final long FAILED_WINDOW_SECONDS_JT = 600;

    private final GroupRepoMa groupsJt;
    private final MemberRepoJt membersJt;
    private final RuleRepoMa rulesJt;
    private final SessionRepoMa sessionsJt;
    private final EventRepoMa eventsJt;
    private final DeviceRepoJt devicesJt;
    private final Clock clockJt;
    private final Map<Long, Deque<Instant>> failedCodesJt = new ConcurrentHashMap<>();

    public GroupServiceJt(GroupRepoMa groupsJt, MemberRepoJt membersJt, RuleRepoMa rulesJt,
            SessionRepoMa sessionsJt, EventRepoMa eventsJt, DeviceRepoJt devicesJt, Clock clockJt) {
        this.sessionsJt = sessionsJt;
        this.eventsJt = eventsJt;
        this.groupsJt = groupsJt;
        this.membersJt = membersJt;
        this.rulesJt = rulesJt;
        this.devicesJt = devicesJt;
        this.clockJt = clockJt;
    }

    private static String newCodeJt() {
        StringBuilder sbJt = new StringBuilder(9);
        for (int posJt = 0; posJt < 8; posJt++) {
            if (posJt == 4) {
                sbJt.append('-');
            }
            sbJt.append(ALPHABET_JT.charAt(RANDOM_JT.nextInt(ALPHABET_JT.length())));
        }
        return sbJt.toString();
    }

    // ------------------------------------------------------------------ grupos

    @Transactional
    public GroupOutJt create(DeviceJtMa deviceJt, String nameJt) {
        String cleanJt = nameJt == null ? "" : nameJt.trim();
        if (cleanJt.isEmpty() || cleanJt.length() > 80) {
            throw ApiExceptionMa.badRequest("El nombre del grupo debe tener entre 1 y 80 caracteres.");
        }
        String codeJt;
        do {
            codeJt = newCodeJt();
        } while (groupsJt.existsByCode(codeJt));
        Instant nowJt = Instant.now(clockJt);
        CloudGroupMa groupJt = groupsJt.save(new CloudGroupMa(cleanJt, codeJt, deviceJt.getId(), nowJt));
        MemberJt ownerJt = membersJt.save(new MemberJt(groupJt.getId(), deviceJt.getId(), deviceJt.getName(),
                MemberJt.OWNER, MemberJt.ACTIVE, nowJt));
        return new GroupOutJt(groupJt.getId(), groupJt.getName(), groupJt.getCode(), MemberJt.OWNER, ownerJt.getId(),
                MemberJt.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<GroupOutJt> list(DeviceJtMa deviceJt) {
        List<GroupOutJt> outJt = new ArrayList<>();
        for (MemberJt memberJt : membersJt.findByDeviceId(deviceJt.getId())) {
            CloudGroupMa groupJt = groupsJt.findById(memberJt.getGroupId()).orElse(null);
            if (groupJt != null) {
                boolean ownerJt = MemberJt.OWNER.equals(memberJt.getRole());
                outJt.add(new GroupOutJt(groupJt.getId(), groupJt.getName(), ownerJt ? groupJt.getCode() : null,
                        memberJt.getRole(), memberJt.getId(), memberJt.getStatus()));
            }
        }
        return outJt;
    }

    /** Se une con el codigo: queda "pendiente" hasta que el dueño lo acepte. */
    @Transactional
    public GroupOutJt join(DeviceJtMa deviceJt, String codeJt, String displayNameJt) {
        Instant nowJt = Instant.now(clockJt);
        checkNotBlockedJt(deviceJt.getId(), nowJt);
        String normalizedJt = codeJt == null ? "" : codeJt.trim().toUpperCase(Locale.ROOT);
        CloudGroupMa groupJt = groupsJt.findByCode(normalizedJt).orElse(null);
        if (groupJt == null) {
            registerFailureJt(deviceJt.getId(), nowJt);
            throw ApiExceptionMa.notFound("Ese código no existe.");
        }
        MemberJt existingJt = membersJt.findByGroupIdAndDeviceId(groupJt.getId(), deviceJt.getId()).orElse(null);
        if (existingJt != null) {
            return new GroupOutJt(groupJt.getId(), groupJt.getName(), null, existingJt.getRole(), existingJt.getId(),
                    existingJt.getStatus());
        }
        String nameJt = displayNameJt == null || displayNameJt.isBlank() ? deviceJt.getName() : displayNameJt.trim();
        if (nameJt.length() > 80) {
            throw ApiExceptionMa.badRequest("El nombre es demasiado largo.");
        }
        MemberJt memberJt = membersJt.save(new MemberJt(groupJt.getId(), deviceJt.getId(), nameJt, MemberJt.MEMBER,
                MemberJt.PENDING, nowJt));
        return new GroupOutJt(groupJt.getId(), groupJt.getName(), null, MemberJt.MEMBER, memberJt.getId(),
                MemberJt.PENDING);
    }

    private void checkNotBlockedJt(Long deviceIdJt, Instant nowJt) {
        Deque<Instant> failsJt = failedCodesJt.get(deviceIdJt);
        if (failsJt == null) {
            return;
        }
        while (!failsJt.isEmpty() && failsJt.peekFirst().plusSeconds(FAILED_WINDOW_SECONDS_JT).isBefore(nowJt)) {
            failsJt.pollFirst();
        }
        if (failsJt.size() >= MAX_FAILED_CODES_JT) {
            throw new ApiExceptionMa(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera unos minutos.");
        }
    }

    private void registerFailureJt(Long deviceIdJt, Instant nowJt) {
        failedCodesJt.computeIfAbsent(deviceIdJt, keyJt -> new ConcurrentLinkedDeque<>()).addLast(nowJt);
    }

    // ------------------------------------------------------------------ miembros (lo hace el dueño)

    /** El dueño ve todos los miembros; el resto solo se ve a si mismo. */
    @Transactional
    public List<MemberOutMa> members(DeviceJtMa deviceJt, Long groupIdJt) {
        MemberJt selfJt = membersJt.findByGroupIdAndDeviceId(groupIdJt, deviceJt.getId())
                .orElseThrow(() -> ApiExceptionMa.notFound("No perteneces a ese grupo."));
        Instant nowJt = Instant.now(clockJt);
        List<MemberJt> visibleJt = MemberJt.OWNER.equals(selfJt.getRole())
                ? membersJt.findByGroupIdOrderByIdAsc(groupIdJt) : List.of(selfJt);
        List<MemberOutMa> outJt = new ArrayList<>();
        for (MemberJt memberJt : visibleJt) {
            expireIfNeededJt(memberJt, nowJt);
            Instant seenJt = devicesJt.findById(memberJt.getDeviceId()).map(DeviceJtMa::getLastSeen).orElse(null);
            ensureSessionJt(memberJt, nowJt);
            boolean historyJt = !sessionsJt.findByMemberIdOrderByStartedAtAsc(memberJt.getId()).isEmpty();
            outJt.add(new MemberOutMa(memberJt.getId(), memberJt.getDisplayName(), memberJt.getRole(),
                    memberJt.getStatus(), memberJt.getControlStatus(), memberJt.getControlKind(),
                    memberJt.getControlFrom(), memberJt.getControlUntil(), memberJt.isMemberCanLeave(),
                    memberJt.controlActive(nowJt), seenJt, historyJt));
        }
        return outJt;
    }

    @Transactional
    public void approve(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        memberJt.setStatus(MemberJt.ACTIVE);
        membersJt.save(memberJt);
    }

    /** Rechaza una solicitud o saca a un miembro del grupo (y borra sus reglas). */
    @Transactional
    public void remove(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        if (MemberJt.OWNER.equals(memberJt.getRole())) {
            throw ApiExceptionMa.badRequest("El dueño no se puede sacar del grupo.");
        }
        rulesJt.deleteByMemberId(memberJt.getId());
        sessionsJt.deleteByMemberId(memberJt.getId());
        membersJt.delete(memberJt);
    }

    /**
     * El dueño elimina el grupo: se van todas las personas, sus reglas, el control y el historial de ese grupo. Cada PC
     * vuelve a quedar libre en su próxima consulta. No se puede deshacer.
     */
    @Transactional
    public void deleteGroup(DeviceJtMa ownerJt, Long groupIdJt) {
        CloudGroupMa groupJt = groupsJt.findById(groupIdJt)
                .orElseThrow(() -> ApiExceptionMa.notFound("No existe ese grupo."));
        if (!groupJt.getOwnerDeviceId().equals(ownerJt.getId())) {
            throw ApiExceptionMa.forbidden("Solo quien creó el grupo puede eliminarlo.");
        }
        for (MemberJt memberJt : membersJt.findByGroupIdOrderByIdAsc(groupIdJt)) {
            rulesJt.deleteByMemberId(memberJt.getId());
            sessionsJt.deleteByMemberId(memberJt.getId());
            membersJt.delete(memberJt);
        }
        groupsJt.delete(groupJt);
    }

    /** El dueño solicita control. No se aplica nada hasta que el miembro lo acepte en su equipo. */
    @Transactional
    public void requestControl(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, ControlInJt inJt) {
        MemberJt memberJt = memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        if (!MemberJt.MEMBER.equals(memberJt.getRole()) || !MemberJt.ACTIVE.equals(memberJt.getStatus())) {
            throw ApiExceptionMa.badRequest("Primero acepta a la persona en el grupo.");
        }
        Instant nowJt = Instant.now(clockJt);
        String kindJt = inJt == null ? null : inJt.kind();
        if (MemberJt.PERMANENT.equals(kindJt)) {
            memberJt.requestControl(MemberJt.PERMANENT, nowJt, null, inJt.memberCanLeave());
        } else if (MemberJt.TIMED.equals(kindJt)) {
            if (inJt.until() == null || !inJt.until().isAfter(nowJt)) {
                throw ApiExceptionMa.badRequest("La hora de fin debe ser futura.");
            }
            Instant fromJt = inJt.from() == null || inJt.from().isBefore(nowJt) ? nowJt : inJt.from();
            if (!inJt.until().isAfter(fromJt)) {
                throw ApiExceptionMa.badRequest("La hora de fin debe ser posterior a la de inicio.");
            }
            memberJt.requestControl(MemberJt.TIMED, fromJt, inJt.until(), inJt.memberCanLeave());
        } else {
            throw ApiExceptionMa.badRequest("El tipo de control debe ser PERMANENT o TIMED.");
        }
        rulesJt.deleteByMemberId(memberJt.getId());
        membersJt.save(memberJt);
    }

    /** El dueño reemplaza todas las reglas de un miembro. Solo con el control aceptado. */
    @Transactional
    public void setRules(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt, List<RuleDtoMa> rulesInJt) {
        MemberJt memberJt = memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        expireIfNeededJt(memberJt, Instant.now(clockJt));
        if (!memberJt.controlActive(Instant.now(clockJt))) {
            throw ApiExceptionMa.forbidden("Esa persona todavía no aceptó el control (o ya terminó).");
        }
        if (rulesInJt != null && rulesInJt.size() > 500) {
            throw ApiExceptionMa.badRequest("Demasiadas reglas.");
        }
        rulesJt.deleteByMemberId(memberJt.getId());
        if (rulesInJt != null) {
            for (RuleDtoMa ruleJt : rulesInJt) {
                if (ruleJt.targetKey() == null || ruleJt.targetKey().isBlank() || ruleJt.kind() == null
                        || ruleJt.mode() == null) {
                    throw ApiExceptionMa.badRequest("Cada regla necesita tipo, objetivo y modo.");
                }
                rulesJt.save(new RemoteRuleMa(memberJt.getId(), ruleJt.kind(), ruleJt.targetKey(),
                        ruleJt.displayName() == null ? ruleJt.targetKey() : ruleJt.displayName(), ruleJt.mode(),
                        ruleJt.state() == null ? "ACTIVE" : ruleJt.state(), ruleJt.daysMask(), ruleJt.startMinute(),
                        ruleJt.endMinute(), ruleJt.quotaMinutes()));
            }
        }
    }

    @Transactional
    public List<RuleDtoMa> rulesOf(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        MemberJt memberJt = memberOfOwnerJt(ownerJt, groupIdJt, memberIdJt);
        expireIfNeededJt(memberJt, Instant.now(clockJt));
        return toDtosJt(memberJt);
    }

    // ------------------------------------------------------------------ miembro (lo hace la persona)

    /** La persona acepta o rechaza el control solicitado, desde su propio equipo. */
    @Transactional
    public void respondControl(DeviceJtMa deviceJt, Long memberIdJt, boolean acceptJt) {
        MemberJt memberJt = ownMemberJt(deviceJt, memberIdJt);
        expireIfNeededJt(memberJt, Instant.now(clockJt));
        if (!MemberJt.REQUESTED.equals(memberJt.getControlStatus())) {
            throw ApiExceptionMa.badRequest("No hay ninguna solicitud de control pendiente.");
        }
        if (acceptJt) {
            memberJt.setControlStatus(MemberJt.ACCEPTED);
            Instant nowJt = Instant.now(clockJt);
            Instant fromJt = memberJt.getControlFrom();
            sessionsJt.save(new ControlSessionMa(memberJt.getId(), fromJt != null && fromJt.isAfter(nowJt) ? fromJt : nowJt));
        } else {
            memberJt.clearControl();
        }
        membersJt.save(memberJt);
    }

    /**
     * Termina el control. El dueño siempre puede. La persona solo si el dueño lo permitio al solicitarlo
     * (por ejemplo, un alumno si, un hijo no).
     */
    @Transactional
    public void endControl(DeviceJtMa deviceJt, Long memberIdJt) {
        MemberJt memberJt = membersJt.findById(memberIdJt)
                .orElseThrow(() -> ApiExceptionMa.notFound("No existe ese miembro."));
        CloudGroupMa groupJt = groupsJt.findById(memberJt.getGroupId()).orElseThrow();
        boolean isOwnerJt = groupJt.getOwnerDeviceId().equals(deviceJt.getId());
        boolean isSelfJt = memberJt.getDeviceId().equals(deviceJt.getId());
        if (!isOwnerJt && !isSelfJt) {
            throw ApiExceptionMa.forbidden("No puedes cambiar a esa persona.");
        }
        if (!isOwnerJt && MemberJt.ACCEPTED.equals(memberJt.getControlStatus()) && !memberJt.isMemberCanLeave()) {
            throw ApiExceptionMa.forbidden("Quien creó el grupo no permite terminar este control desde aquí.");
        }
        rulesJt.deleteByMemberId(memberJt.getId());
        closeSessionJt(memberJt, Instant.now(clockJt));
        memberJt.clearControl();
        membersJt.save(memberJt);
    }

    /** La persona sale del grupo (si tiene control aceptado, solo si el dueño lo permite). */
    @Transactional
    public void leave(DeviceJtMa deviceJt, Long memberIdJt) {
        MemberJt memberJt = ownMemberJt(deviceJt, memberIdJt);
        if (MemberJt.OWNER.equals(memberJt.getRole())) {
            throw ApiExceptionMa.badRequest("El dueño no puede salir de su propio grupo.");
        }
        expireIfNeededJt(memberJt, Instant.now(clockJt));
        if (MemberJt.ACCEPTED.equals(memberJt.getControlStatus()) && !memberJt.isMemberCanLeave()) {
            throw ApiExceptionMa.forbidden("Quien creó el grupo no permite salir mientras haya control.");
        }
        rulesJt.deleteByMemberId(memberJt.getId());
        sessionsJt.deleteByMemberId(memberJt.getId());
        membersJt.delete(memberJt);
    }

    // ------------------------------------------------------------------ apoyo

    /** Si el control por horario ya termino, vuelve a "ninguno" y borra las reglas que venian de el. */
    void expireIfNeededJt(MemberJt memberJt, Instant nowJt) {
        if (memberJt.controlEnded(nowJt)) {
            rulesJt.deleteByMemberId(memberJt.getId());
            Instant untilJt = memberJt.getControlUntil();
            closeSessionJt(memberJt, untilJt != null && untilJt.isBefore(nowJt) ? untilJt : nowJt);
            memberJt.clearControl();
            membersJt.save(memberJt);
        }
    }

    /** Cierra el tramo de control que estaba abierto (si lo había). */
    void closeSessionJt(MemberJt memberJt, Instant endJt) {
        sessionsJt.findFirstByMemberIdAndEndedAtIsNullOrderByStartedAtDesc(memberJt.getId()).ifPresent(sessionJt -> {
            sessionJt.end(endJt.isBefore(sessionJt.getStartedAt()) ? sessionJt.getStartedAt() : endJt);
            sessionsJt.save(sessionJt);
        });
    }

    /**
     * Si hay control vigente pero no hay tramo abierto (control anterior a esta función), lo abre ahora. Y si el control
     * ya había terminado antes de que existieran los tramos, se arma uno con lo que el equipo alcanzó a subir
     * (del primer al último registro) para que quien controló pueda seguir viendo ese historial.
     */
    void ensureSessionJt(MemberJt memberJt, Instant nowJt) {
        if (memberJt.controlActive(nowJt)
                && sessionsJt.findFirstByMemberIdAndEndedAtIsNullOrderByStartedAtDesc(memberJt.getId()).isEmpty()) {
            Instant fromJt = memberJt.getControlFrom();
            sessionsJt.save(new ControlSessionMa(memberJt.getId(), fromJt != null && fromJt.isBefore(nowJt) ? fromJt : nowJt));
            return;
        }
        if (MemberJt.MEMBER.equals(memberJt.getRole()) && MemberJt.NONE.equals(memberJt.getControlStatus())
                && sessionsJt.findByMemberIdOrderByStartedAtAsc(memberJt.getId()).isEmpty()) {
            var firstJt = eventsJt.findFirstByDeviceIdOrderByStartedAtAsc(memberJt.getDeviceId());
            var lastJt = eventsJt.findFirstByDeviceIdOrderByEndedAtDesc(memberJt.getDeviceId());
            if (firstJt.isPresent() && lastJt.isPresent()) {
                ControlSessionMa legacyJt = new ControlSessionMa(memberJt.getId(), firstJt.get().getStartedAt());
                legacyJt.end(lastJt.get().getEndedAt());
                sessionsJt.save(legacyJt);
            }
        }
    }

    /** Todos los tramos de control de ese miembro, del más viejo al más nuevo. */
    List<ControlSessionMa> sessionsOfJt(Long memberIdJt) {
        return sessionsJt.findByMemberIdOrderByStartedAtAsc(memberIdJt);
    }

    /** El tramo abierto ahora (su inicio), o null. */
    Instant openSessionStartJt(Long memberIdJt) {
        return sessionsJt.findFirstByMemberIdAndEndedAtIsNullOrderByStartedAtDesc(memberIdJt)
                .map(ControlSessionMa::getStartedAt).orElse(null);
    }

    List<RuleDtoMa> toDtosJt(MemberJt memberJt) {
        List<RuleDtoMa> outJt = new ArrayList<>();
        for (RemoteRuleMa ruleJt : rulesJt.findByMemberId(memberJt.getId())) {
            outJt.add(new RuleDtoMa(ruleJt.getKind(), ruleJt.getTargetKey(), ruleJt.getDisplayName(),
                    ruleJt.getMode(), ruleJt.getState(), ruleJt.getDaysMask(), ruleJt.getStartMinute(),
                    ruleJt.getEndMinute(), ruleJt.getQuotaMinutes()));
        }
        return outJt;
    }

    /** El miembro pedido, comprobando que quien llama es el dueño de ese grupo. */
    MemberJt memberOfOwnerJt(DeviceJtMa ownerJt, Long groupIdJt, Long memberIdJt) {
        CloudGroupMa groupJt = groupsJt.findById(groupIdJt)
                .orElseThrow(() -> ApiExceptionMa.notFound("No existe ese grupo."));
        if (!groupJt.getOwnerDeviceId().equals(ownerJt.getId())) {
            throw ApiExceptionMa.forbidden("Solo quien creó el grupo puede hacer esto.");
        }
        MemberJt memberJt = membersJt.findById(memberIdJt)
                .orElseThrow(() -> ApiExceptionMa.notFound("No existe ese miembro."));
        if (!memberJt.getGroupId().equals(groupIdJt)) {
            throw ApiExceptionMa.notFound("Ese miembro no es de este grupo.");
        }
        return memberJt;
    }

    private MemberJt ownMemberJt(DeviceJtMa deviceJt, Long memberIdJt) {
        MemberJt memberJt = membersJt.findById(memberIdJt)
                .orElseThrow(() -> ApiExceptionMa.notFound("No existe ese miembro."));
        if (!memberJt.getDeviceId().equals(deviceJt.getId())) {
            throw ApiExceptionMa.forbidden("Esa membresía no es de este equipo.");
        }
        return memberJt;
    }
}
