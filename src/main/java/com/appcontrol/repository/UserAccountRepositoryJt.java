package com.appcontrol.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.model.UserAccountJtMa;

public interface UserAccountRepositoryJt extends JpaRepository<UserAccountJtMa, Long> {

    Optional<UserAccountJtMa> findByUsernameKey(String usernameKey);
}
