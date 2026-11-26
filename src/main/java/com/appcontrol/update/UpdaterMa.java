package com.appcontrol.update;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * El ayudante de la actualización. Corre aparte, desde una copia del programa, cuando AppControl ya se está cerrando:
 * espera a que termine, cambia `app/appcontrol.jar` por el nuevo (guardando el anterior por si algo falla) y abre de nuevo
 * AppControl. Usa solo Java; no abre consolas ni scripts. Argumentos: id del proceso, jar nuevo, jar instalado, AppControl.exe.
 */
public final class UpdaterMa {

    private UpdaterMa() {
    }

    public static void main(String[] argsMa) throws Exception {
        long pidMa = Long.parseLong(argsMa[0]);
        Path newJarMa = Path.of(argsMa[1]);
        Path targetMa = Path.of(argsMa[2]);
        Path exeMa = Path.of(argsMa[3]);
        Path logMa = newJarMa.getParent().resolve("update.log");
        try (PrintWriter outMa = new PrintWriter(Files.newBufferedWriter(logMa))) {
            outMa.println(Instant.now() + " Actualizando " + targetMa);
            boolean okMa = replaceMa(pidMa, newJarMa, targetMa, outMa);
            outMa.println(Instant.now() + (okMa ? " Listo, abriendo AppControl" : " No se pudo cambiar; se abre la versión anterior"));
            outMa.flush();
            new ProcessBuilder(exeMa.toString()).directory(exeMa.getParent().toFile()).start();
        }
    }

    /** Espera a que el proceso termine y cambia el archivo; true si quedó la versión nueva. */
    static boolean replaceMa(long pidMa, Path newJarMa, Path targetMa, PrintWriter logMa)
            throws IOException, InterruptedException {
        Optional<ProcessHandle> handleMa = ProcessHandle.of(pidMa);
        if (handleMa.isPresent()) {
            try {
                handleMa.get().onExit().get(90, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exMa) {
                logMa.println("AppControl no terminó a tiempo: " + exMa);
            }
        }
        Path backupMa = targetMa.resolveSibling(targetMa.getFileName() + ".bak");
        for (int tryMa = 1; tryMa <= 60; tryMa++) {
            try {
                Files.copy(targetMa, backupMa, StandardCopyOption.REPLACE_EXISTING);
                Files.copy(newJarMa, targetMa, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(newJarMa);
                return true;
            } catch (IOException exMa) {
                // el archivo puede seguir en uso unos segundos más
                logMa.println("Intento " + tryMa + ": " + exMa.getMessage());
                Thread.sleep(500);
            }
        }
        try {
            if (Files.isRegularFile(backupMa)) {
                Files.copy(backupMa, targetMa, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exMa) {
            logMa.println("No se pudo restaurar: " + exMa.getMessage());
        }
        return false;
    }
}
