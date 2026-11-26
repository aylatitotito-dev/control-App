package com.appcontrol.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** La cuenta del dueno de este equipo. Solo guarda huellas (hash) de la contrasena y del codigo de recuperacion. */
@Entity
@Table(name = "user_account")
public class UserAccountJtMa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username_key", nullable = false, unique = true, length = 60)
    private String usernameKey;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 300)
    private String passwordHash;

    @Column(name = "recovery_hash", nullable = false, length = 300)
    private String recoveryHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UserAccountJtMa() {
    }

    public UserAccountJtMa(String usernameKey, String displayName, String passwordHash, String recoveryHash) {
        this.usernameKey = usernameKey;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.recoveryHash = recoveryHash;
    }

    /** Cambia el nombre de la cuenta (y con él, el usuario con el que se inicia sesión). */
    public void renameJt(String newKeyJt, String newDisplayJt) {
        this.usernameKey = newKeyJt;
        this.displayName = newDisplayJt;
    }

    public void changePasswordJt(String newHashJt) {
        this.passwordHash = newHashJt;
    }

    public void changeRecoveryJt(String newHashJt) {
        this.recoveryHash = newHashJt;
    }

    public Long getId() {
        return id;
    }

    public String getUsernameKey() {
        return usernameKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRecoveryHash() {
        return recoveryHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
