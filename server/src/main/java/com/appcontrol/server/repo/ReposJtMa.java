package com.appcontrol.server.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.server.model.CloudGroupMa;
import com.appcontrol.server.model.ControlSessionMa;
import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.model.EventRowJtMa;
import com.appcontrol.server.model.InstalledRowMa;
import com.appcontrol.server.model.MemberJt;
import com.appcontrol.server.model.RemoteRuleMa;
import com.appcontrol.server.model.UsageRowJt;

/** Todos los repositorios de la API juntos (son interfaces de una linea cada uno). */
public final class ReposJtMa {

    private ReposJtMa() {
    }

    public interface DeviceRepoJt extends JpaRepository<DeviceJtMa, Long> {
        Optional<DeviceJtMa> findByPublicId(String publicId);
    }

    public interface GroupRepoMa extends JpaRepository<CloudGroupMa, Long> {
        Optional<CloudGroupMa> findByCode(String code);

        boolean existsByCode(String code);
    }

    public interface MemberRepoJt extends JpaRepository<MemberJt, Long> {
        List<MemberJt> findByDeviceId(Long deviceId);

        List<MemberJt> findByGroupIdOrderByIdAsc(Long groupId);

        Optional<MemberJt> findByGroupIdAndDeviceId(Long groupId, Long deviceId);
    }

    public interface SessionRepoMa extends JpaRepository<ControlSessionMa, Long> {
        List<ControlSessionMa> findByMemberIdOrderByStartedAtAsc(Long memberId);

        Optional<ControlSessionMa> findFirstByMemberIdAndEndedAtIsNullOrderByStartedAtDesc(Long memberId);

        void deleteByMemberId(Long memberId);
    }

    public interface RuleRepoMa extends JpaRepository<RemoteRuleMa, Long> {
        List<RemoteRuleMa> findByMemberId(Long memberId);

        void deleteByMemberId(Long memberId);
    }

    public interface UsageRepoJt extends JpaRepository<UsageRowJt, Long> {
        Optional<UsageRowJt> findByDeviceIdAndAppKeyAndDay(Long deviceId, String appKey, LocalDate day);

        List<UsageRowJt> findByDeviceIdAndDayBetweenOrderByDayAsc(Long deviceId, LocalDate from, LocalDate to);
    }

    public interface EventRepoMa extends JpaRepository<EventRowJtMa, Long> {
        Optional<EventRowJtMa> findByDeviceIdAndClientId(Long deviceId, long clientId);

        Optional<EventRowJtMa> findFirstByDeviceIdOrderByStartedAtAsc(Long deviceId);

        Optional<EventRowJtMa> findFirstByDeviceIdOrderByEndedAtDesc(Long deviceId);

        List<EventRowJtMa> findByDeviceIdAndStartedAtBetweenOrderByStartedAtDesc(Long deviceId, Instant from,
                Instant to);
    }

    public interface AppRepoJt extends JpaRepository<InstalledRowMa, Long> {
        List<InstalledRowMa> findByDeviceIdOrderByDisplayNameAsc(Long deviceId);

        void deleteByDeviceId(Long deviceId);
    }
}
