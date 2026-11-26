package com.appcontrol.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La actualización solo se acepta si el archivo está completo, con su huella y firmado con la llave del autor. */
class UpdateServiceJtMaTest {

    @TempDir
    Path dirJtMa;

    private static KeyPair keysJtMa() throws Exception {
        KeyPairGenerator genJtMa = KeyPairGenerator.getInstance("RSA");
        genJtMa.initialize(2048);
        return genJtMa.generateKeyPair();
    }

    private static String signJtMa(Path fileJtMa, KeyPair keysJtMa) throws Exception {
        Signature sigJtMa = Signature.getInstance("SHA256withRSA");
        sigJtMa.initSign(keysJtMa.getPrivate());
        sigJtMa.update(Files.readAllBytes(fileJtMa));
        return Base64.getEncoder().encodeToString(sigJtMa.sign());
    }

    @Test
    void aceptaUnArchivoCompletoYBienFirmado() throws Exception {
        Path jarJtMa = dirJtMa.resolve("nuevo.jar");
        Files.write(jarJtMa, "contenido de la version nueva".getBytes(StandardCharsets.UTF_8));
        KeyPair keysJtMa = keysJtMa();
        UpdateInfoJtMa infoJtMa = new UpdateInfoJtMa("20261125120000", Files.size(jarJtMa),
                UpdateServiceJt.sha256Jt(jarJtMa), signJtMa(jarJtMa, keysJtMa));
        UpdateServiceJt.verifyJt(jarJtMa, infoJtMa, keysJtMa.getPublic());
    }

    @Test
    void rechazaArchivoAlteradoIncompletoOFirmadoPorOtro() throws Exception {
        Path jarJtMa = dirJtMa.resolve("nuevo.jar");
        Files.write(jarJtMa, "contenido original".getBytes(StandardCharsets.UTF_8));
        KeyPair keysJtMa = keysJtMa();
        UpdateInfoJtMa goodJtMa = new UpdateInfoJtMa("20261125120000", Files.size(jarJtMa),
                UpdateServiceJt.sha256Jt(jarJtMa), signJtMa(jarJtMa, keysJtMa));

        // alguien cambió el archivo después de firmarlo
        Path tamperedJtMa = dirJtMa.resolve("alterado.jar");
        Files.write(tamperedJtMa, "contenido ALTERADO".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> UpdateServiceJt.verifyJt(tamperedJtMa, goodJtMa, keysJtMa.getPublic()));

        // llegó cortado
        Path shortJtMa = dirJtMa.resolve("corto.jar");
        Files.write(shortJtMa, "conte".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> UpdateServiceJt.verifyJt(shortJtMa, goodJtMa, keysJtMa.getPublic()));

        // huella y tamaño bien, pero firmado con otra llave (por ejemplo, un servidor falso)
        KeyPair otherJtMa = keysJtMa();
        UpdateInfoJtMa forgedJtMa = new UpdateInfoJtMa("20261125120000", Files.size(jarJtMa),
                UpdateServiceJt.sha256Jt(jarJtMa), signJtMa(jarJtMa, otherJtMa));
        assertThrows(IOException.class, () -> UpdateServiceJt.verifyJt(jarJtMa, forgedJtMa, keysJtMa.getPublic()));
    }

    @Test
    void laLlaveQueVaEnLaAplicacionSeLee() throws Exception {
        assertEquals("RSA", UpdateServiceJt.publicKeyJt().getAlgorithm());
    }

    @Test
    void soloOfreceVersionesMasNuevasYNuncaEnDesarrollo() {
        // en las pruebas no hay build.properties filtrado por la instalación: se trata como compilación de Maven
        assertTrue(UpdateServiceJt.isNewerJt("99991231235959"));
        assertFalse(UpdateServiceJt.isNewerJt("20000101000000"));
        assertFalse(UpdateServiceJt.isNewerJt("dev"));
        assertFalse(UpdateServiceJt.isNewerJt(""));
    }

    @Test
    void elAyudanteCambiaElArchivoYGuardaLaCopiaAnterior() throws Exception {
        Path targetJtMa = dirJtMa.resolve("appcontrol.jar");
        Path newJtMa = dirJtMa.resolve("appcontrol-new.jar");
        Files.writeString(targetJtMa, "version vieja");
        Files.writeString(newJtMa, "version nueva");
        StringWriter textJtMa = new StringWriter();
        // el proceso 0 no existe: no hay nada que esperar
        boolean okJtMa = UpdaterMa.replaceMa(Long.MAX_VALUE - 7, newJtMa, targetJtMa, new PrintWriter(textJtMa));
        assertTrue(okJtMa, textJtMa.toString());
        assertEquals("version nueva", Files.readString(targetJtMa));
        assertEquals("version vieja", Files.readString(dirJtMa.resolve("appcontrol.jar.bak")));
        assertFalse(Files.exists(newJtMa), "el archivo descargado se borra al terminar");
    }
}
