package com.appcontrol.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** La imagen de pantalla se comprime a JPEG liviano y se puede volver a leer del otro lado. */
class ScreenShareJtTest {

    @Test
    void laImagenSeComprimeAJpegYSeLee() throws Exception {
        BufferedImage imageJt = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2Jt = imageJt.createGraphics();
        g2Jt.setColor(new Color(0x2563EB));
        g2Jt.fillRect(0, 0, 1280, 720);
        g2Jt.dispose();

        byte[] jpegJt = ScreenShareJt.jpegJt(imageJt);

        assertTrue(jpegJt.length > 100 && jpegJt.length < 900_000, "tamaño razonable: " + jpegJt.length);
        assertEquals((byte) 0xFF, jpegJt[0]);
        assertEquals((byte) 0xD8, jpegJt[1]);
        BufferedImage backJt = ImageIO.read(new ByteArrayInputStream(jpegJt));
        assertNotNull(backJt);
        assertEquals(1280, backJt.getWidth());
        assertEquals(720, backJt.getHeight());
    }
}
