package com.appcontrol.server.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ofrece la versión más nueva de la aplicación de escritorio para que las copias instaladas se actualicen solas. El archivo
 * y su ficha (`latest.json`: número de compilación, tamaño, huella SHA-256 y firma) se preparan con
 * `build-tools/preparar_actualizacion.py` y viajan dentro de este servidor. Es público (no lleva datos de nadie); la
 * aplicación solo instala lo que venga firmado con la llave del autor.
 */
@RestController
@RequestMapping("/api/app")
public class UpdateApiMa {

    private static final String INFO_MA = "updates/latest.json";
    private static final String JAR_MA = "updates/appcontrol.jar";

    private final ObjectMapper jsonMa = new ObjectMapper();

    @GetMapping(value = "/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> latest() throws IOException {
        ClassPathResource infoMa = new ClassPathResource(INFO_MA);
        if (!infoMa.exists() || !new ClassPathResource(JAR_MA).exists()) {
            return ResponseEntity.ok("{\"available\":false}");
        }
        try (InputStream streamMa = infoMa.getInputStream()) {
            return ResponseEntity.ok(new String(streamMa.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download() throws IOException {
        ClassPathResource jarMa = new ClassPathResource(JAR_MA);
        ClassPathResource infoMa = new ClassPathResource(INFO_MA);
        if (!jarMa.exists() || !infoMa.exists()) {
            return ResponseEntity.notFound().build();
        }
        long sizeMa;
        try (InputStream streamMa = infoMa.getInputStream()) {
            JsonNode nodeMa = jsonMa.readTree(streamMa);
            sizeMa = nodeMa.path("size").asLong(-1);
        }
        ResponseEntity.BodyBuilder builderMa = ResponseEntity.ok().contentType(MediaType.parseMediaType("application/java-archive"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=appcontrol.jar");
        if (sizeMa > 0) {
            builderMa.contentLength(sizeMa);
        }
        return builderMa.body(new InputStreamResource(jarMa.getInputStream()));
    }
}
