package com.appcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SiteNormalizeJtTest {

    @Test
    void urlCompletaConPuertoQuedaComoDominio() {
        assertEquals("youtube.com", RuleServiceJt.normalizeSiteJtMa("https://www.YouTube.com:8080/watch?v=1#t"));
    }

    @Test
    void dominioSimpleYSubdominio() {
        assertEquals("poker.net", RuleServiceJt.normalizeSiteJtMa("  poker.net "));
        assertEquals("juegos.ejemplo.com", RuleServiceJt.normalizeSiteJtMa("juegos.ejemplo.com/ruta"));
    }

    @Test
    void credencialesSeDescartan() {
        assertEquals("sitio.com", RuleServiceJt.normalizeSiteJtMa("http://user:pass@sitio.com/x"));
    }

    @Test
    void entradasInvalidasFallan() {
        assertThrows(IllegalArgumentException.class, () -> RuleServiceJt.normalizeSiteJtMa(""));
        assertThrows(IllegalArgumentException.class, () -> RuleServiceJt.normalizeSiteJtMa("hola"));
        assertThrows(IllegalArgumentException.class, () -> RuleServiceJt.normalizeSiteJtMa("a b.com"));
        assertThrows(IllegalArgumentException.class, () -> RuleServiceJt.normalizeSiteJtMa(null));
    }

    @Test
    void codigoDeGrupoTieneFormatoLegible() {
        for (int iJt = 0; iJt < 200; iJt++) {
            assertTrue(GroupServiceMa.generateCodeJt().matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"));
        }
    }
}
