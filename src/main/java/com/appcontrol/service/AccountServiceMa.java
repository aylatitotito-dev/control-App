package com.appcontrol.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.UserAccountJtMa;
import com.appcontrol.repository.UserAccountRepositoryJt;

/**
 * La cuenta del dueno del equipo: crear cuenta, iniciar y cerrar sesion, comprobar la contrasena antes de tocar
 * las reglas, cambiarla y recuperarla con el codigo que se muestra una sola vez al crearla.
 * Tras 5 intentos incorrectos seguidos hay una espera obligatoria que crece (30 s, 1 min, 2 min... hasta 5 min).
 */
@Service
public class AccountServiceMa {

    public static final int MIN_PASSWORD_JT = 8;

    /** Resultado de comprobar una contrasena: bien, mal, o bloqueado por demasiados intentos. */
    public enum ResultJt { OK, WRONG, LOCKED }

    private static final String SESSION_KEY_JT = "session_active";
    private static final int FREE_ATTEMPTS_JT = 5;
    private static final long BASE_LOCK_MS_JT = 30_000;
    private static final long MAX_LOCK_MS_JT = 300_000;
    private static final String CODE_ALPHABET_JT = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM_JT = new SecureRandom();

    private final UserAccountRepositoryJt repoJt;
    private final AppSettingsServiceMa settingsJt;
    private LongSupplier clockJt = System::currentTimeMillis;
    private int failuresJt;
    private long lockedUntilMsJt;
    private long nextLockMsJt = BASE_LOCK_MS_JT;

    public AccountServiceMa(UserAccountRepositoryJt repoJt, AppSettingsServiceMa settingsJt) {
        this.repoJt = repoJt;
        this.settingsJt = settingsJt;
    }

    /** Solo para pruebas: reloj que se puede adelantar. */
    void setClockJt(LongSupplier clockJt) {
        this.clockJt = clockJt;
    }

    /** Solo para pruebas: olvida los intentos fallidos y la espera. */
    synchronized void resetAttemptsJt() {
        failuresJt = 0;
        lockedUntilMsJt = 0;
        nextLockMsJt = BASE_LOCK_MS_JT;
    }

    @Transactional(readOnly = true)
    public boolean hasAccount() {
        return repoJt.count() > 0;
    }

    /** Nombre con el que se creo la cuenta, o "" si todavia no hay cuenta. */
    @Transactional(readOnly = true)
    public String usernameJt() {
        return repoJt.findAll().stream().findFirst().map(UserAccountJtMa::getDisplayName).orElse("");
    }

    private static final String AVATAR_KEY_JT = "avatar_color";

    /** Color del círculo con la inicial ("#RRGGBB"), o "" si no se eligió ninguno. */
    public String avatarColorJt() {
        return settingsJt.get(AVATAR_KEY_JT, "");
    }

    /**
     * Editar perfil: cambia el nombre y el color del avatar. Pide la contraseña actual (igual que cualquier cambio
     * que afecte a la cuenta). El nombre nuevo es también el usuario para iniciar sesión.
     */
    @Transactional
    public ResultJt updateProfileJt(String passwordJt, String newNameJt, String avatarColorJt) {
        String nameJt = newNameJt == null ? "" : newNameJt.trim();
        if (!nameJt.matches("[\\p{L}0-9._@-]{3,40}")) {
            throw new IllegalArgumentException("El usuario debe tener de 3 a 40 caracteres: letras, números, "
                    + "punto, guion o @.");
        }
        String colorJt = avatarColorJt == null ? "" : avatarColorJt.trim();
        if (!colorJt.isEmpty() && !colorJt.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("El color no es válido.");
        }
        ResultJt resultJt = verifyPasswordJt(passwordJt);
        if (resultJt == ResultJt.OK) {
            UserAccountJtMa accJt = repoJt.findAll().get(0);
            accJt.renameJt(keyOf(nameJt), nameJt);
            repoJt.save(accJt);
            settingsJt.set(AVATAR_KEY_JT, colorJt);
        }
        return resultJt;
    }

    /** Segundos que faltan para poder volver a intentar (0 si no hay bloqueo). */
    public synchronized long lockSecondsLeftJt() {
        long leftMsJt = lockedUntilMsJt - clockJt.getAsLong();
        return leftMsJt <= 0 ? 0 : (leftMsJt + 999) / 1000;
    }

    static String keyOf(String usernameJt) {
        return usernameJt == null ? "" : usernameJt.trim().toLowerCase(Locale.ROOT);
    }

    static void checkPassword(String passwordJt) {
        if (passwordJt == null || passwordJt.length() < MIN_PASSWORD_JT) {
            throw new IllegalArgumentException("La contraseña debe tener al menos " + MIN_PASSWORD_JT + " caracteres.");
        }
    }

    /** "ABCD-EFGH-JKLM-NPQR" */
    static String newRecoveryCode() {
        StringBuilder sbJt = new StringBuilder();
        for (int posJt = 0; posJt < 16; posJt++) {
            if (posJt > 0 && posJt % 4 == 0) {
                sbJt.append('-');
            }
            sbJt.append(CODE_ALPHABET_JT.charAt(RANDOM_JT.nextInt(CODE_ALPHABET_JT.length())));
        }
        return sbJt.toString();
    }

    private static String normalizeCode(String codeJt) {
        return codeJt == null ? "" : codeJt.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Crea la cuenta (solo puede haber una en este equipo) y deja la sesion iniciada.
     * Devuelve el codigo de recuperacion, que SOLO se puede ver ahora.
     */
    @Transactional
    public String registerJt(String usernameJt, String passwordJt) {
        String nameJt = usernameJt == null ? "" : usernameJt.trim();
        if (!nameJt.matches("[\\p{L}0-9._@-]{3,40}")) {
            throw new IllegalArgumentException("El usuario debe tener de 3 a 40 caracteres: letras, números, "
                    + "punto, guion o @.");
        }
        checkPassword(passwordJt);
        if (repoJt.count() > 0) {
            throw new IllegalStateException("Ya existe una cuenta en este equipo.");
        }
        String codeJt = newRecoveryCode();
        repoJt.save(new UserAccountJtMa(keyOf(nameJt), nameJt, PasswordHashJt.hashJt(passwordJt),
                PasswordHashJt.hashJt(normalizeCode(codeJt))));
        settingsJt.set(SESSION_KEY_JT, "true");
        return codeJt;
    }

    /** Comprueba una contrasena aplicando la espera por intentos fallidos. */
    private synchronized ResultJt checkAgainstJt(boolean okJt) {
        if (lockSecondsLeftJt() > 0) {
            return ResultJt.LOCKED;
        }
        if (okJt) {
            failuresJt = 0;
            nextLockMsJt = BASE_LOCK_MS_JT;
            return ResultJt.OK;
        }
        failuresJt++;
        if (failuresJt >= FREE_ATTEMPTS_JT) {
            lockedUntilMsJt = clockJt.getAsLong() + nextLockMsJt;
            nextLockMsJt = Math.min(MAX_LOCK_MS_JT, nextLockMsJt * 2);
            failuresJt = 0;
            return ResultJt.LOCKED;
        }
        return ResultJt.WRONG;
    }

    /** Iniciar sesion. Si "rememberJt", la proxima vez que se abra la app no pide usuario ni contrasena. */
    @Transactional
    public ResultJt loginJt(String usernameJt, String passwordJt, boolean rememberJt) {
        if (lockSecondsLeftJt() > 0) {
            return ResultJt.LOCKED;
        }
        boolean okJt = repoJt.findByUsernameKey(keyOf(usernameJt))
                .map(accJt -> PasswordHashJt.matchesJt(accJt.getPasswordHash(), passwordJt)).orElse(false);
        ResultJt resultJt = checkAgainstJt(okJt);
        if (resultJt == ResultJt.OK) {
            settingsJt.set(SESSION_KEY_JT, String.valueOf(rememberJt));
        }
        return resultJt;
    }

    /** Pide la contrasena para una accion sobre reglas (no cambia la sesion). */
    @Transactional(readOnly = true)
    public ResultJt verifyPasswordJt(String passwordJt) {
        if (lockSecondsLeftJt() > 0) {
            return ResultJt.LOCKED;
        }
        boolean okJt = repoJt.findAll().stream().findFirst()
                .map(accJt -> PasswordHashJt.matchesJt(accJt.getPasswordHash(), passwordJt)).orElse(false);
        return checkAgainstJt(okJt);
    }

    @Transactional
    public ResultJt changePasswordJt(String oldPasswordJt, String newPasswordJt) {
        checkPassword(newPasswordJt);
        ResultJt resultJt = verifyPasswordJt(oldPasswordJt);
        if (resultJt == ResultJt.OK) {
            UserAccountJtMa accJt = repoJt.findAll().get(0);
            accJt.changePasswordJt(PasswordHashJt.hashJt(newPasswordJt));
            repoJt.save(accJt);
        }
        return resultJt;
    }

    /**
     * Olvidaste la contrasena: con el codigo de recuperacion se pone una nueva. Devuelve el codigo NUEVO
     * (el anterior deja de servir) o null si el codigo no era correcto / hay bloqueo (ver lockSecondsLeftJt).
     */
    @Transactional
    public String resetWithRecoveryJt(String recoveryCodeJt, String newPasswordJt) {
        checkPassword(newPasswordJt);
        if (lockSecondsLeftJt() > 0) {
            return null;
        }
        UserAccountJtMa accJt = repoJt.findAll().stream().findFirst().orElse(null);
        boolean okJt = accJt != null && PasswordHashJt.matchesJt(accJt.getRecoveryHash(), normalizeCode(recoveryCodeJt));
        if (checkAgainstJt(okJt) != ResultJt.OK) {
            return null;
        }
        String newCodeJt = newRecoveryCode();
        accJt.changePasswordJt(PasswordHashJt.hashJt(newPasswordJt));
        accJt.changeRecoveryJt(PasswordHashJt.hashJt(normalizeCode(newCodeJt)));
        repoJt.save(accJt);
        settingsJt.set(SESSION_KEY_JT, "true");
        return newCodeJt;
    }

    /** true si hay cuenta y la sesion se dejo iniciada ("mantener sesion"). */
    public boolean isSessionActiveJt() {
        return hasAccount() && settingsJt.getBoolean(SESSION_KEY_JT, false);
    }

    public void logoutJt() {
        settingsJt.set(SESSION_KEY_JT, "false");
    }
}
