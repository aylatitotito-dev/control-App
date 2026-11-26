package com.appcontrol.server.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Grupo (Casa, Clase...) con su codigo de invitacion. */
@Entity
@Table(name = "cloud_group")
public class CloudGroupMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 9)
    private String code;

    @Column(name = "owner_device_id", nullable = false)
    private Long ownerDeviceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CloudGroupMa() {
    }

    public CloudGroupMa(String name, String code, Long ownerDeviceId, Instant createdAt) {
        this.name = name;
        this.code = code;
        this.ownerDeviceId = ownerDeviceId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public Long getOwnerDeviceId() {
        return ownerDeviceId;
    }
}
