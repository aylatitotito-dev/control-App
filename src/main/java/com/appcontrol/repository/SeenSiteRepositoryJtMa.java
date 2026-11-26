package com.appcontrol.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.appcontrol.model.SeenSiteJtMa;

public interface SeenSiteRepositoryJtMa extends JpaRepository<SeenSiteJtMa, Long> {

    Optional<SeenSiteJtMa> findByHost(String host);

    List<SeenSiteJtMa> findAllByOrderByLastSeenDesc();
}
