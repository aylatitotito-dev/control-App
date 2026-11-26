package com.appcontrol.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.model.ActivityEventJt;

public interface ActivityEventRepositoryMa extends JpaRepository<ActivityEventJt, Long> {

    List<ActivityEventJt> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(Instant from,
            Instant to);

    Optional<ActivityEventJt> findTopByKindOrderByEndedAtDesc(String kind);

    Optional<ActivityEventJt> findTopByKindAndTargetKeyOrderByEndedAtDesc(String kind, String targetKey);

    long deleteByStartedAtBefore(Instant before);
}
