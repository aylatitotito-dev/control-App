package com.appcontrol.cloud;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mensajes que viajan entre la app y la API de la nube (el mismo formato JSON que usa el servidor). */
public final class CloudDtoJt {

    private CloudDtoJt() {
    }

    public record RegisterOutJt(String token, String publicId) {
    }

    /** Un grupo visto por quien lo consulta (el codigo solo lo trae si es el dueño). */
    public record GroupOutMa(Long id, String name, String code, String role, Long memberId, String status) {
    }

    public record MemberOutJt(Long id, String displayName, String role, String status, String controlStatus,
            String controlKind, Instant controlFrom, Instant controlUntil, boolean memberCanLeave,
            boolean controlActive, Instant lastSeen, boolean hasHistory) {
    }

    /** Un tramo en que ese equipo estuvo bajo control (endedAt null = sigue en marcha). */
    public record SessionDtoJt(Instant startedAt, Instant endedAt) {
    }

    public record ControlInMa(String kind, Instant from, Instant until, boolean memberCanLeave) {
    }

    public record RuleDtoJt(String kind, String targetKey, String displayName, String mode, String state,
            int daysMask, int startMinute, int endMinute, int quotaMinutes) {
    }

    public record SyncMembershipMa(Long memberId, Long groupId, String groupName, String ownerName, String role,
            String status, String controlStatus, String controlKind, Instant controlFrom, Instant controlUntil,
            boolean memberCanLeave, Instant sessionStart, List<RuleDtoJt> rules) {
    }

    public record SyncOutJt(Instant serverTime, List<SyncMembershipMa> memberships) {
    }

    public record UsageDtoMa(String appKey, String displayName, LocalDate day, long seconds) {
    }

    public record EventDtoJt(long clientId, String type, String targetKey, String displayName, Instant startedAt,
            Instant endedAt, int hits) {
    }

    public record AppDtoMa(String appKey, String displayName) {
    }
}
