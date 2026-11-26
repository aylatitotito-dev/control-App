package com.appcontrol.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.LongConsumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.appcontrol.BuildInfoMa;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Actualizar AppControl sin reinstalar: pregunta al servidor si hay una compilación más nueva, la descarga, comprueba su
 * huella y su firma digital (la llave pública va dentro de la aplicación) y, al aplicarla, un ayudante pequeño espera a
 * que esta copia se cierre, cambia el archivo `app/appcontrol.jar` y vuelve a abrir AppControl. Si algo no coincide, no
 * se instala nada. Solo funciona en una copia instalada; al correr desde el código no ofrece actualizaciones.
 */
@Component
public class UpdateServiceJt {

    private final HttpClient httpJt = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper jsonJt = new ObjectMapper();
    private final String baseUrlJt;

    public UpdateServiceJt(
            @Value("${appcontrol.cloud.url:https://appcontrol-api-50205.azurewebsites.net}") String baseUrlJt) {
        this.baseUrlJt = baseUrlJt.endsWith("/") ? baseUrlJt.substring(0, baseUrlJt.length() - 1) : baseUrlJt;
    }

    // ------------------------------------------------------------------ dónde está instalada esta copia

    /** El archivo `appcontrol.jar` que está corriendo, o null si no se puede saber. */
    static Path runningJarJt() {
        // Con Spring Boot la ubicación del código es "jar:nested:..." y no sirve como ruta: el jar está en java.class.path.
        try {
            String cpJt = System.getProperty("java.class.path", "");
            for (String partJt : cpJt.split(java.io.File.pathSeparator)) {
                if (partJt.toLowerCase().endsWith(".jar")) {
                    Path pJt = Path.of(partJt).toAbsolutePath();
                    if (Files.isRegularFile(pJt)) {
                        return pJt;
                    }
                }
            }
        } catch (Exception exJt) {
            // se prueba la otra forma
        }
        try {
            Path pathJt = Path.of(UpdateServiceJt.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(pathJt) ? pathJt : null;
        } catch (Exception exJt) {
            return null;
        }
    }

    /** `AppControl.exe` de la instalación (la carpeta que contiene a `app`), o null si no hay una instalada. */
    static Path installedExeJt(Path jarJt) {
        if (jarJt == null || jarJt.getParent() == null || jarJt.getParent().getParent() == null
                || !"app".equalsIgnoreCase(jarJt.getParent().getFileName().toString())) {
            return null;
        }
        Path exeJt = jarJt.getParent().getParent().resolve("AppControl.exe");
        return Files.isRegularFile(exeJt) ? exeJt : null;
    }

    /** ¿Esta copia puede actualizarse sola? (instalada con el instalador y no en modo desarrollo) */
    public boolean canApplyJt() {
        return !BuildInfoMa.isDevMa() && installedExeJt(runningJarJt()) != null;
    }

    private static Path updateDirJt() throws IOException {
        String baseJt = System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home");
        Path dirJt = Path.of(baseJt, "AppControl", "update");
        Files.createDirectories(dirJt);
        return dirJt;
    }

    // ------------------------------------------------------------------ buscar

    /** La versión más nueva que ofrece el servidor, solo si es posterior a la que está corriendo. Sin internet: vacío. */
    public Optional<UpdateInfoJtMa> checkJt() {
        if (BuildInfoMa.isDevMa()) {
            return Optional.empty();
        }
        try {
            HttpRequest reqJt = HttpRequest.newBuilder(URI.create(baseUrlJt + "/api/app/latest"))
                    .timeout(Duration.ofSeconds(60)).header("Accept", "application/json").GET().build();
            HttpResponse<String> respJt = httpJt.send(reqJt, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respJt.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode nodeJt = jsonJt.readTree(respJt.body());
            if (!nodeJt.path("available").asBoolean(false)) {
                return Optional.empty();
            }
            UpdateInfoJtMa infoJt = new UpdateInfoJtMa(nodeJt.path("build").asText(""), nodeJt.path("size").asLong(0),
                    nodeJt.path("sha256").asText(""), nodeJt.path("signature").asText(""));
            return isNewerJt(infoJt.build()) ? Optional.of(infoJt) : Optional.empty();
        } catch (IOException exJt) {
            return Optional.empty();
        } catch (InterruptedException exJt) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    static boolean isNewerJt(String serverBuildJt) {
        return serverBuildJt.matches("\\d{14}") && serverBuildJt.compareTo(BuildInfoMa.buildIdMa()) > 0;
    }

    // ------------------------------------------------------------------ descargar y comprobar

    /**
     * Descarga la versión nueva a la carpeta de actualización y la comprueba (tamaño, huella SHA-256 y firma). Si algo no
     * coincide borra lo descargado y lanza un error: nunca queda instalable un archivo dudoso.
     */
    public Path downloadJt(UpdateInfoJtMa infoJt, LongConsumer progressJt) throws IOException, InterruptedException {
        Path dirJt = updateDirJt();
        Path partJt = dirJt.resolve("appcontrol-new.jar.part");
        Path finalJt = dirJt.resolve("appcontrol-new.jar");
        Files.deleteIfExists(finalJt);
        HttpRequest reqJt = HttpRequest.newBuilder(URI.create(baseUrlJt + "/api/app/download"))
                .timeout(Duration.ofMinutes(15)).GET().build();
        HttpResponse<InputStream> respJt = httpJt.send(reqJt, HttpResponse.BodyHandlers.ofInputStream());
        if (respJt.statusCode() != 200) {
            throw new IOException("El servidor no entregó la actualización (" + respJt.statusCode() + ").");
        }
        try (InputStream inJt = respJt.body(); OutputStream outJt = Files.newOutputStream(partJt)) {
            byte[] bufferJt = new byte[64 * 1024];
            long totalJt = 0;
            int readJt;
            while ((readJt = inJt.read(bufferJt)) > 0) {
                outJt.write(bufferJt, 0, readJt);
                totalJt += readJt;
                if (progressJt != null) {
                    progressJt.accept(totalJt);
                }
            }
        }
        try {
            verifyJt(partJt, infoJt, publicKeyJt());
        } catch (IOException | RuntimeException exJt) {
            Files.deleteIfExists(partJt);
            throw exJt;
        }
        Files.move(partJt, finalJt, StandardCopyOption.REPLACE_EXISTING);
        return finalJt;
    }

    /** Comprueba tamaño, huella y firma de un archivo descargado. Lanza IOException si algo no coincide. */
    static void verifyJt(Path fileJt, UpdateInfoJtMa infoJt, PublicKey keyJt) throws IOException {
        if (infoJt.size() > 0 && Files.size(fileJt) != infoJt.size()) {
            throw new IOException("La actualización llegó incompleta.");
        }
        String shaJt = sha256Jt(fileJt);
        if (!shaJt.equalsIgnoreCase(infoJt.sha256())) {
            throw new IOException("La huella de la actualización no coincide.");
        }
        try {
            Signature sigJt = Signature.getInstance("SHA256withRSA");
            sigJt.initVerify(keyJt);
            try (InputStream inJt = Files.newInputStream(fileJt)) {
                byte[] bufferJt = new byte[64 * 1024];
                int readJt;
                while ((readJt = inJt.read(bufferJt)) > 0) {
                    sigJt.update(bufferJt, 0, readJt);
                }
            }
            if (!sigJt.verify(Base64.getMimeDecoder().decode(infoJt.signature()))) {
                throw new IOException("La firma de la actualización no es válida: no se instala.");
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exJt) {
            throw new IOException("No se pudo comprobar la firma de la actualización.", exJt);
        }
    }

    static String sha256Jt(Path fileJt) throws IOException {
        try (InputStream inJt = Files.newInputStream(fileJt)) {
            MessageDigest digestJt = MessageDigest.getInstance("SHA-256");
            byte[] bufferJt = new byte[64 * 1024];
            int readJt;
            while ((readJt = inJt.read(bufferJt)) > 0) {
                digestJt.update(bufferJt, 0, readJt);
            }
            return HexFormat.of().formatHex(digestJt.digest());
        } catch (java.security.NoSuchAlgorithmException exJt) {
            throw new IOException(exJt);
        }
    }

    /** La llave pública (va dentro de la aplicación): solo lo firmado con la llave privada del autor se acepta. */
    static PublicKey publicKeyJt() throws IOException {
        try (InputStream inJt = UpdateServiceJt.class.getResourceAsStream("/update-public.b64")) {
            if (inJt == null) {
                throw new IOException("Esta copia no trae la llave de actualizaciones.");
            }
            byte[] derJt = Base64.getMimeDecoder().decode(new String(inJt.readAllBytes(), StandardCharsets.US_ASCII).trim());
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(derJt));
        } catch (java.security.GeneralSecurityException exJt) {
            throw new IOException("La llave de actualizaciones no es válida.", exJt);
        }
    }

    // ------------------------------------------------------------------ aplicar

    /**
     * Prepara el cambio: copia el programa actual a un archivo aparte y arranca con él un ayudante que espera a que esta
     * copia termine, reemplaza `app/appcontrol.jar` por el nuevo y vuelve a abrir AppControl. Quien llama debe cerrar
     * AppControl enseguida después de esto.
     */
    public void applyJt(Path newJarJt) throws IOException {
        Path jarJt = runningJarJt();
        Path exeJt = installedExeJt(jarJt);
        if (exeJt == null) {
            throw new IOException("Esta copia no está instalada con el instalador, así que no puede actualizarse sola.");
        }
        Path helperJt = updateDirJt().resolve("updater.jar");
        Files.copy(jarJt, helperJt, StandardCopyOption.REPLACE_EXISTING);
        Path javaJt = Path.of(System.getProperty("java.home"), "bin", "javaw.exe");
        if (!Files.isRegularFile(javaJt)) {
            javaJt = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        }
        if (!Files.isRegularFile(javaJt)) {
            throw new IOException("Esta instalación es antigua y no trae el ayudante de actualización. "
                    + "Instala una vez más con el AppControl-0.0.1.exe nuevo; después se actualizará sola.");
        }
        new ProcessBuilder(javaJt.toString(), "-cp", helperJt.toString(),
                "-Dloader.main=com.appcontrol.update.UpdaterMa",
                "org.springframework.boot.loader.launch.PropertiesLauncher", String.valueOf(ProcessHandle.current().pid()),
                newJarJt.toString(), jarJt.toString(), exeJt.toString()).directory(updateDirJt().toFile()).start();
    }
}
