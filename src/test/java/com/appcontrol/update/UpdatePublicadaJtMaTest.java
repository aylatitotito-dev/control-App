package com.appcontrol.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Si hay una actualización preparada dentro del servidor (server/src/main/resources/updates), comprueba con la llave pública
 * que lleva la aplicación que su huella y su firma son válidas: así nunca se publica algo que las copias rechazarían.
 */
class UpdatePublicadaJtMaTest {

    @Test
    void laActualizacionPreparadaTieneHuellaYFirmaValidas() throws Exception {
        Path dirJtMa = Path.of("server", "src", "main", "resources", "updates");
        Path infoPathJtMa = dirJtMa.resolve("latest.json");
        Path jarJtMa = dirJtMa.resolve("appcontrol.jar");
        assumeTrue(Files.isRegularFile(infoPathJtMa) && Files.isRegularFile(jarJtMa), "no hay actualización preparada");
        JsonNode nodeJtMa = new ObjectMapper().readTree(Files.readString(infoPathJtMa));
        UpdateInfoJtMa infoJtMa = new UpdateInfoJtMa(nodeJtMa.path("build").asText(), nodeJtMa.path("size").asLong(),
                nodeJtMa.path("sha256").asText(), nodeJtMa.path("signature").asText());
        UpdateServiceJt.verifyJt(jarJtMa, infoJtMa, UpdateServiceJt.publicKeyJt());
        assertEquals(14, infoJtMa.build().length());
    }
}
