package com.appcontrol.server.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mensajes de la API (lo que viaja en JSON). */
public final class ApiDtoJtMa {

    private ApiDtoJtMa() {
    }

    public record RegisterInJt(String name) {
    }

    public record RegisterOutMa(String token, String publicId) {
    }

    public record GroupInJt(String name) {
    }

    public record JoinInMa(String code, String displayName) {
    }

    /** Un grupo visto por quien lo consulta. El codigo solo lo ve el dueño. */
    public record GroupOutJt(Long id, String name, String code, String role, Long memberId, String status) {
    }

    public record MemberOutMa(Long id, String displayName, String role, String status, String controlStatus,
            String controlKind, Instant controlFrom, Instant controlUntil, boolean memberCanLeave,
            boolean controlActive, Instant lastSeen, boolean hasHistory) {
    }

    /** Un tramo en que ese equipo estuvo bajo control (endedAt null = sigue en marcha). */
    public record SessionDtoMa(Instant startedAt, Instant endedAt) {
    }

    public record ControlInJt(String kind, Instant from, Instant until, boolean memberCanLeave) {
    }

    public record RuleDtoMa(String kind, String targetKey, String displayName, String mode, String state,
            int daysMask, int startMinute, int endMinute, int quotaMinutes) {
    }

    public record SyncMembershipJt(Long memberId, Long groupId, String groupName, String ownerName, String role,
            String status, String controlStatus, String controlKind, Instant controlFrom, Instant controlUntil,
            boolean memberCanLeave, Instant sessionStart, List<RuleDtoMa> rules) {
    }

    public record SyncOutMa(Instant serverTime, List<SyncMembershipJt> memberships) {
    }

    public record UsageDtoJt(String appKey, String displayName, LocalDate day, long seconds) {
    }

    public record EventDtoMa(long clientId, String type, String targetKey, String displayName, Instant startedAt,
            Instant endedAt, int hits) {
    }

    public record AppDtoJt(String appKey, String displayName) {
    }
}
