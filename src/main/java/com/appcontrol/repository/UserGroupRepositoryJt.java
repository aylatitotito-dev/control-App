package com.appcontrol.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.model.UserGroupJt;

public interface UserGroupRepositoryJt extends JpaRepository<UserGroupJt, Long> {

    boolean existsByCode(String code);

    List<UserGroupJt> findAllByOrderByCreatedAtDesc();
}
