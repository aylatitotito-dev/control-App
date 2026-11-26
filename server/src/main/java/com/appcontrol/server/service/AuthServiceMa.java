package com.appcontrol.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.server.api.ApiDtoJtMa.RegisterOutMa;
import com.appcontrol.server.api.ApiExceptionMa;
import com.appcontrol.server.model.DeviceJtMa;
import com.appcontrol.server.repo.ReposJtMa.DeviceRepoJt;

/**
 * Identifica a cada equipo. Al registrarse recibe un token "idPublico.secreto"; el secreto solo se muestra una vez
 * y en la base queda unicamente su huella SHA-256 (el secreto es aleatorio de 256 bits, no una contraseña).
 */
@Service
public class AuthServiceMa {

    private static final SecureRandom RANDOM_MA = new SecureRandom();

    private final DeviceRepoJt devicesMa;
    private final Clock clockMa;

    public AuthServiceMa(DeviceRepoJt devicesMa, Clock clockMa) {
        this.devicesMa = devicesMa;
        this.clockMa = clockMa;
    }

    @Transactional
    public RegisterOutMa register(String nameMa) {
        String cleanMa = nameMa == null ? "" : nameMa.trim();
        if (cleanMa.isEmpty() || cleanMa.length() > 80) {
            throw ApiExceptionMa.badRequest("El nombre del equipo debe tener entre 1 y 80 caracteres.");
        }
        byte[] rawMa = new byte[32];
        RANDOM_MA.nextBytes(rawMa);
        String secretMa = Base64.getUrlEncoder().withoutPadding().encodeToString(rawMa);
        String publicIdMa = UUID.randomUUID().toString();
        devicesMa.save(new DeviceJtMa(publicIdMa, cleanMa, hash(secretMa), Instant.now(clockMa)));
        return new RegisterOutMa(publicIdMa + "." + secretMa, publicIdMa);
    }

    /** Devuelve el equipo que corresponde a la cabecera "Authorization: Bearer idPublico.secreto". */
    @Transactional
    public DeviceJtMa authenticate(String headerMa) {
        if (headerMa == null || !headerMa.startsWith("Bearer ")) {
            throw new ApiExceptionMa(HttpStatus.UNAUTHORIZED, "Falta el token del equipo.");
        }
        String tokenMa = headerMa.substring(7).trim();
        int dotMa = tokenMa.indexOf('.');
        if (dotMa <= 0) {
            throw new ApiExceptionMa(HttpStatus.UNAUTHORIZED, "Token no valido.");
        }
        Optional<DeviceJtMa> foundMa = devicesMa.findByPublicId(tokenMa.substring(0, dotMa));
        String expectedMa = foundMa.map(DeviceJtMa::getTokenHash).orElse("0".repeat(64));
        boolean okMa = MessageDigest.isEqual(expectedMa.getBytes(StandardCharsets.US_ASCII),
                hash(tokenMa.substring(dotMa + 1)).getBytes(StandardCharsets.US_ASCII));
        if (foundMa.isEmpty() || !okMa) {
            throw new ApiExceptionMa(HttpStatus.UNAUTHORIZED, "Token no valido.");
        }
        DeviceJtMa deviceMa = foundMa.get();
        deviceMa.setLastSeen(Instant.now(clockMa));
        return deviceMa;
    }

    static String hash(String textMa) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(textMa.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exMa) {
            throw new IllegalStateException(exMa);
        }
    }
}
