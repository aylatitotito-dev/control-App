package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.repository.AppSettingRepositoryMa;
import com.appcontrol.repository.UserAccountRepositoryJt;
import com.appcontrol.service.AccountServiceMa.ResultJt;

/** La cuenta contra una base real: registro, sesion, contrasena, intentos fallidos y recuperacion. */
@DataJpaTest
@Import({AccountServiceMa.class, AppSettingsServiceMa.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext
class AccountServiceJtTest {

    private static final String PASS = "Clave-segura-1";

    @Autowired
    private AccountServiceMa account;

    @Autowired
    private UserAccountRepositoryJt accounts;

    @Autowired
    private AppSettingRepositoryMa settings;

    private final AtomicLong now = new AtomicLong(1_000_000L);

    @BeforeEach
    void clean() {
        accounts.deleteAll();
        settings.deleteAll();
        now.set(1_000_000L);
        account.setClockJt(now::get);
        account.resetAttemptsJt();
    }

    @Test
    void sinCuentaNoHaySesionNiUsuario() {
        assertFalse(account.hasAccount());
        assertFalse(account.isSessionActiveJt());
        assertEquals("", account.usernameJt());
    }

    @Test
    void crearLaCuentaDevuelveElCodigoDeRecuperacionYDejaLaSesionIniciada() {
        String code = account.registerJt("  Juan.Torres ", PASS);
        assertTrue(code.matches("([A-HJ-NP-Z2-9]{4}-){3}[A-HJ-NP-Z2-9]{4}"), code);
        assertTrue(account.hasAccount());
        assertEquals("Juan.Torres", account.usernameJt());
        assertTrue(account.isSessionActiveJt());
    }

    @Test
    void laContrasenaNuncaSeGuardaEnTextoYCadaHuellaEsDistinta() {
        account.registerJt("duenio", PASS);
        String stored = accounts.findAll().get(0).getPasswordHash();
        assertFalse(stored.contains(PASS));
        assertTrue(stored.startsWith("pbkdf2$"));
        assertNotEquals(PasswordHashJt.hashJt(PASS), PasswordHashJt.hashJt(PASS), "la sal hace distinta cada huella");
        assertTrue(PasswordHashJt.matchesJt(stored, PASS));
        assertFalse(PasswordHashJt.matchesJt(stored, PASS + "x"));
    }

    @Test
    void datosInvalidosSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> account.registerJt("ab", PASS));
        assertThrows(IllegalArgumentException.class, () -> account.registerJt("con espacios", PASS));
        assertThrows(IllegalArgumentException.class, () -> account.registerJt("duenio", "corta"));
        assertFalse(account.hasAccount());
        account.registerJt("duenio", PASS);
        assertThrows(IllegalStateException.class, () -> account.registerJt("otro", PASS), "solo una cuenta por equipo");
    }

    @Test
    void iniciarSesionConYSinMantenerla() {
        account.registerJt("Duenio", PASS);
        assertEquals(ResultJt.WRONG, account.loginJt("duenio", "incorrecta1", true));
        assertEquals(ResultJt.OK, account.loginJt("DUENIO", PASS, false));       // el usuario no distingue mayusculas
        assertFalse(account.isSessionActiveJt(), "sin 'mantener sesion' se vuelve a pedir al abrir");
        assertEquals(ResultJt.OK, account.loginJt("duenio", PASS, true));
        assertTrue(account.isSessionActiveJt());
        account.logoutJt();
        assertFalse(account.isSessionActiveJt());
        assertEquals(ResultJt.WRONG, account.loginJt("otro-usuario", PASS, true));
    }

    @Test
    void laContrasenaPedidaParaLasReglasSeVerifica() {
        account.registerJt("duenio", PASS);
        assertEquals(ResultJt.OK, account.verifyPasswordJt(PASS));
        assertEquals(ResultJt.WRONG, account.verifyPasswordJt("otra-clave-1"));
        assertEquals(ResultJt.WRONG, account.verifyPasswordJt(""));
        assertEquals(ResultJt.WRONG, account.verifyPasswordJt(null));
    }

    @Test
    void tras5ErroresHayEsperaYCrece() {
        account.registerJt("duenio", PASS);
        for (int n = 1; n <= 4; n++) {
            assertEquals(ResultJt.WRONG, account.verifyPasswordJt("mala-" + n));
        }
        assertEquals(ResultJt.LOCKED, account.verifyPasswordJt("mala-5"), "el 5.o error bloquea");
        assertEquals(30, account.lockSecondsLeftJt());
        // aun con la contrasena correcta, durante la espera no entra
        assertEquals(ResultJt.LOCKED, account.verifyPasswordJt(PASS));
        assertEquals(ResultJt.LOCKED, account.loginJt("duenio", PASS, true));

        now.addAndGet(31_000);
        assertEquals(0, account.lockSecondsLeftJt());
        // otros 5 errores: la espera ahora es de 60 s
        for (int n = 1; n <= 4; n++) {
            account.verifyPasswordJt("mala");
        }
        assertEquals(ResultJt.LOCKED, account.verifyPasswordJt("mala"));
        assertEquals(60, account.lockSecondsLeftJt());
        now.addAndGet(61_000);
        assertEquals(ResultJt.OK, account.verifyPasswordJt(PASS), "pasada la espera, la correcta entra");
    }

    @Test
    void entrarBienReiniciaLosErrores() {
        account.registerJt("duenio", PASS);
        for (int n = 0; n < 4; n++) {
            account.verifyPasswordJt("mala");
        }
        assertEquals(ResultJt.OK, account.verifyPasswordJt(PASS));
        for (int n = 0; n < 4; n++) {
            assertEquals(ResultJt.WRONG, account.verifyPasswordJt("mala"), "el contador empezo de cero");
        }
    }

    @Test
    void cambiarLaContrasena() {
        account.registerJt("duenio", PASS);
        assertEquals(ResultJt.WRONG, account.changePasswordJt("no-es-esta", "Nueva-clave-22"));
        assertEquals(ResultJt.OK, account.verifyPasswordJt(PASS), "no cambio");
        assertThrows(IllegalArgumentException.class, () -> account.changePasswordJt(PASS, "corta"));
        assertEquals(ResultJt.OK, account.changePasswordJt(PASS, "Nueva-clave-22"));
        assertEquals(ResultJt.WRONG, account.verifyPasswordJt(PASS));
        assertEquals(ResultJt.OK, account.verifyPasswordJt("Nueva-clave-22"));
    }

    @Test
    void recuperarConElCodigoYElCodigoViejoDejaDeServir() {
        String oldCode = account.registerJt("duenio", PASS);
        assertNull(account.resetWithRecoveryJt("AAAA-BBBB-CCCC-DDDD", "Clave-nueva-33"), "codigo incorrecto");
        assertEquals(ResultJt.OK, account.verifyPasswordJt(PASS), "sigue la contrasena de antes");

        // el codigo se acepta en minusculas y sin guiones
        String newCode = account.resetWithRecoveryJt(oldCode.toLowerCase().replace("-", ""), "Clave-nueva-33");
        assertNotNull(newCode);
        assertNotEquals(oldCode, newCode);
        assertEquals(ResultJt.OK, account.verifyPasswordJt("Clave-nueva-33"));
        assertEquals(ResultJt.WRONG, account.verifyPasswordJt(PASS));
        assertNull(account.resetWithRecoveryJt(oldCode, "Otra-clave-44"), "el codigo viejo ya no sirve");
        assertNotNull(account.resetWithRecoveryJt(newCode, "Otra-clave-44"));
    }

    @Test
    void adivinarElCodigoDeRecuperacionTambienSeBloquea() {
        account.registerJt("duenio", PASS);
        for (int n = 0; n < 5; n++) {
            assertNull(account.resetWithRecoveryJt("AAAA-AAAA-AAAA-AAA" + n, "Clave-nueva-33"));
        }
        assertTrue(account.lockSecondsLeftJt() > 0);
    }

    @Test
    void editarElPerfilCambiaNombreYColorPidiendoLaContrasena() {
        account.registerJt("Duenio", PASS);
        account.logoutJt();
        // Con contraseña mala no cambia nada.
        assertEquals(ResultJt.WRONG, account.updateProfileJt("mala-contrasena", "Juana.Perez", "#E5484D"));
        assertEquals("Duenio", account.usernameJt());
        assertEquals("", account.avatarColorJt());
        // Con la contraseña buena sí: el nombre nuevo también es el usuario para iniciar sesión.
        assertEquals(ResultJt.OK, account.updateProfileJt(PASS, "  Juana.Perez ", "#E5484D"));
        assertEquals("Juana.Perez", account.usernameJt());
        assertEquals("#E5484D", account.avatarColorJt());
        assertEquals(ResultJt.OK, account.loginJt("juana.perez", PASS, true));
        assertEquals(ResultJt.WRONG, account.loginJt("Duenio", PASS, true), "el nombre viejo ya no entra");
        // Datos inválidos se rechazan sin tocar la cuenta.
        assertThrows(IllegalArgumentException.class, () -> account.updateProfileJt(PASS, "ab", ""));
        assertThrows(IllegalArgumentException.class, () -> account.updateProfileJt(PASS, "Juana.Perez", "rojo"));
        assertEquals("Juana.Perez", account.usernameJt());
        // Volver al color automático.
        assertEquals(ResultJt.OK, account.updateProfileJt(PASS, "Juana.Perez", ""));
        assertEquals("", account.avatarColorJt());
    }
}
