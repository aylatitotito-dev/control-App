package com.appcontrol.cloud;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRuleRepositoryMa extends JpaRepository<GroupRuleJtMa, Long> {

    List<GroupRuleJtMa> findByKind(String kind);

    @org.springframework.transaction.annotation.Transactional
    void deleteByMemberId(Long memberId);
}
