package com.appcontrol.tracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Del titulo de la ventana del navegador al dominio, con los formatos reales de Chrome, Edge y Firefox. */
class SiteTitleIndexJtTest {

    private static SiteTitleIndexJt index() {
        SiteTitleIndexJt idx = new SiteTitleIndexJt();
        idx.learnJt("WhatsApp", "whatsapp.com");
        idx.learnJt("YouTube", "youtube.com");
        idx.learnJt("Mi curso de redes - Bitlearning", "bitlearning.com");
        idx.learnJt("Inicio", "uno.com");
        idx.learnJt("Inicio", "otro.com");
        idx.learnJt("Nueva pestaña", "algo.com");
        return idx;
    }

    @Test
    void whatsappEnChromeConYSinContadorDeMensajes() {
        SiteTitleIndexJt idx = index();
        assertEquals("whatsapp.com", idx.domainForJt("WhatsApp - Google Chrome"));
        assertEquals("whatsapp.com", idx.domainForJt("(3) WhatsApp - Google Chrome"));
        assertEquals("whatsapp.com", idx.domainForJt("(12) WhatsApp - Persona 1 - Google Chrome"));
    }

    @Test
    void edgeFirefoxYBrave() {
        SiteTitleIndexJt idx = index();
        assertEquals("whatsapp.com", idx.domainForJt("WhatsApp - Personal - Microsoft​ Edge"));
        assertEquals("youtube.com", idx.domainForJt("YouTube and 3 more pages - Personal - Microsoft Edge"));
        assertEquals("youtube.com", idx.domainForJt("YouTube y 2 páginas más - Personal - Microsoft Edge"));
        assertEquals("whatsapp.com", idx.domainForJt("WhatsApp — Mozilla Firefox"));
        assertEquals("youtube.com", idx.domainForJt("YouTube - Brave"));
    }

    @Test
    void titulosConGuionPropioSiguenFuncionando() {
        assertEquals("bitlearning.com", index().domainForJt("Mi curso de redes - Bitlearning - Google Chrome"));
    }

    @Test
    void siNoSePuedeSaberConSeguridadDevuelveNulo() {
        SiteTitleIndexJt idx = index();
        assertNull(idx.domainForJt("Inicio - Google Chrome"), "el mismo titulo en dos sitios es ambiguo");
        assertNull(idx.domainForJt("Nueva pestaña - Google Chrome"), "pestana nueva");
        assertNull(idx.domainForJt("Google Chrome"));
        assertNull(idx.domainForJt("Una pagina que nunca se visito - Google Chrome"));
        assertNull(idx.domainForJt(null));
        assertNull(idx.domainForJt("   "));
    }

    @Test
    void soloLosNavegadoresSeConsideran() {
        assertTrue(SiteTitleIndexJt.isBrowserJt("chrome"));
        assertTrue(SiteTitleIndexJt.isBrowserJt("msedge"));
        assertTrue(SiteTitleIndexJt.isBrowserJt("firefox"));
        assertFalse(SiteTitleIndexJt.isBrowserJt("notepad"));
        assertFalse(SiteTitleIndexJt.isBrowserJt("steam"));
        assertFalse(SiteTitleIndexJt.isBrowserJt(null));
    }
}
