package com.appcontrol.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.model.BlockRuleJtMa;

public interface BlockRuleRepositoryJt extends JpaRepository<BlockRuleJtMa, Long> {

    Optional<BlockRuleJtMa> findByKindAndTargetKey(String kind, String targetKey);

    List<BlockRuleJtMa> findByKindOrderByDisplayNameAsc(String kind);

    List<BlockRuleJtMa> findAllByOrderByDisplayNameAsc();
}
