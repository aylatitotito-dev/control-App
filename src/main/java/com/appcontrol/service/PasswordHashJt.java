package com.appcontrol.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Huella de contrasenas con PBKDF2-HMAC-SHA256 y sal propia. Formato: "pbkdf2$vueltas$sal$huella". */
public final class PasswordHashJt {

    private static final int ITERATIONS_JT = 120_000;
    private static final int KEY_BITS_JT = 256;
    private static final SecureRandom RANDOM_JT = new SecureRandom();

    private PasswordHashJt() {
    }

    public static String hashJt(String secretJt) {
        byte[] saltJt = new byte[16];
        RANDOM_JT.nextBytes(saltJt);
        return format(ITERATIONS_JT, saltJt, derive(secretJt, saltJt, ITERATIONS_JT));
    }

    public static boolean matchesJt(String storedJt, String secretJt) {
        if (storedJt == null || secretJt == null) {
            return false;
        }
        String[] partsJt = storedJt.split("\\$");
        if (partsJt.length != 4 || !"pbkdf2".equals(partsJt[0])) {
            return false;
        }
        try {
            int roundsJt = Integer.parseInt(partsJt[1]);
            byte[] saltJt = Base64.getDecoder().decode(partsJt[2]);
            byte[] expectedJt = Base64.getDecoder().decode(partsJt[3]);
            byte[] actualJt = derive(secretJt, saltJt, roundsJt);
            return MessageDigest.isEqual(expectedJt, actualJt);
        } catch (IllegalArgumentException exJt) {
            return false;
        }
    }

    private static byte[] derive(String secretJt, byte[] saltJt, int roundsJt) {
        try {
            PBEKeySpec specJt = new PBEKeySpec(secretJt.toCharArray(), saltJt, roundsJt, KEY_BITS_JT);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specJt).getEncoded();
        } catch (Exception exJt) {
            throw new IllegalStateException("No se pudo calcular la huella de la contraseña", exJt);
        }
    }

    private static String format(int roundsJt, byte[] saltJt, byte[] hashJt) {
        return "pbkdf2$" + roundsJt + "$" + Base64.getEncoder().encodeToString(saltJt) + "$"
                + Base64.getEncoder().encodeToString(hashJt);
    }
}
