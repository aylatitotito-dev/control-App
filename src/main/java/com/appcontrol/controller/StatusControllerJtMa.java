package com.appcontrol.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.appcontrol.dto.StatusDtoJt;
import com.appcontrol.service.StatusServiceJtMa;

@RestController
@RequestMapping("/api")
public class StatusControllerJtMa {

    private final StatusServiceJtMa statusServiceMa;

    public StatusControllerJtMa(StatusServiceJtMa statusServiceMa) {
        this.statusServiceMa = statusServiceMa;
    }

    /** 200 si la BD responde; 503 si no. */
    @GetMapping("/status")
    public ResponseEntity<StatusDtoJt> statusMa() {
        StatusDtoJt dtoMa = statusServiceMa.checkMa();
        return dtoMa.dbUp() ? ResponseEntity.ok(dtoMa) : ResponseEntity.status(503).body(dtoMa);
    }
}
