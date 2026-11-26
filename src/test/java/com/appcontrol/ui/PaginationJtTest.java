package com.appcontrol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;

/** Paginas de 10 en 10 y boton de actualizar en segundo plano. */
class PaginationJtTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /** Pantalla de prueba con "total" elementos. */
    static final class FakePageJt extends BrowsePageJt {
        private final int totalJt;
        final CountDownLatch startedJt = new CountDownLatch(1);
        final CountDownLatch releaseJt = new CountDownLatch(1);
        final AtomicBoolean refreshedJt = new AtomicBoolean();

        FakePageJt(int totalJt) {
            super("Prueba", "Prueba");
            this.totalJt = totalJt;
            buildMa();
        }

        @Override
        ResultJt loadMa(String queryJt, String filterNameJt, String sortNameJt) {
            List<BrowseItemMa> itemsJt = new ArrayList<>();
            for (int posJt = 1; posJt <= totalJt; posJt++) {
                String nameJt = "sitio" + posJt + ".com";
                if (queryJt.isEmpty() || nameJt.contains(queryJt)) {
                    itemsJt.add(new BrowseItemMa(nameJt, "visto hoy", "Sitio web", ChipMa.KindJtMa.NEUTRAL, null, null,
                            null));
                }
            }
            return new ResultJt(itemsJt, totalJt);
        }

        @Override
        String[] filtersMa() {
            return new String[0];
        }

        @Override
        String[] sortsMa() {
            return new String[0];
        }

        @Override
        String placeholderMa() {
            return "Buscar";
        }

        @Override
        String nounMa(int amountJt) {
            return amountJt == 1 ? "sitio" : "sitios";
        }

        @Override
        void refreshNowMa() {
            startedJt.countDown();
            try {
                releaseJt.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exJt) {
                Thread.currentThread().interrupt();
            }
            refreshedJt.set(true);
        }
    }

    private static int count(Container rootJt, Class<?> typeJt) {
        int nJt = 0;
        for (Component childJt : rootJt.getComponents()) {
            if (typeJt.isInstance(childJt)) {
                nJt++;
            }
            if (childJt instanceof Container cJt) {
                nJt += count(cJt, typeJt);
            }
        }
        return nJt;
    }

    private static JButton findButton(Container rootJt, String textJt) {
        for (Component childJt : rootJt.getComponents()) {
            if (childJt instanceof JButton bJt && textJt.equals(bJt.getText())) {
                return bJt;
            }
            if (childJt instanceof Container cJt) {
                JButton foundJt = findButton(cJt, textJt);
                if (foundJt != null) {
                    return foundJt;
                }
            }
        }
        return null;
    }

    private static JButton findByTooltip(Container rootJt, String startsJt) {
        for (Component childJt : rootJt.getComponents()) {
            if (childJt instanceof JButton bJt && bJt.getToolTipText() != null
                    && bJt.getToolTipText().startsWith(startsJt)) {
                return bJt;
            }
            if (childJt instanceof Container cJt) {
                JButton foundJt = findByTooltip(cJt, startsJt);
                if (foundJt != null) {
                    return foundJt;
                }
            }
        }
        return null;
    }

    private static void layoutAll(Container cJt) {
        cJt.doLayout();
        for (Component childJt : cJt.getComponents()) {
            if (childJt instanceof Container ccJt) {
                layoutAll(ccJt);
            }
        }
    }

    @Test
    void lasCuentasDeLasPaginas() {
        assertEquals(1, PagerJt.pagesFor(0));
        assertEquals(1, PagerJt.pagesFor(10));
        assertEquals(2, PagerJt.pagesFor(11));
        assertEquals(3, PagerJt.pagesFor(25));
        assertEquals(2, PagerJt.clampJt(9, 25));
        assertEquals(0, PagerJt.clampJt(-3, 25));
        List<Integer> allJt = new ArrayList<>();
        for (int nJt = 1; nJt <= 25; nJt++) {
            allJt.add(nJt);
        }
        assertEquals(10, PagerJt.sliceJt(allJt, 0).size());
        assertEquals(11, PagerJt.sliceJt(allJt, 1).get(0));
        assertEquals(List.of(21, 22, 23, 24, 25), PagerJt.sliceJt(allJt, 2));
        assertTrue(PagerJt.sliceJt(allJt, 7).isEmpty());
    }

    @Test
    void veinticincoElementosSalenDe10En10YSeNavega() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            new ThemeManagerMa().applyJt(false);
            FakePageJt pageJt = new FakePageJt(25);
            pageJt.setSize(1000, 700);
            layoutAll(pageJt);

            assertEquals(10, count(pageJt, ChipMa.class), "pagina 1");
            JButton nextJt = findButton(pageJt, "Siguiente  ›");
            JButton prevJt = findButton(pageJt, "‹  Anterior");
            assertTrue(nextJt.isEnabled());
            assertFalse(prevJt.isEnabled(), "en la primera pagina no hay anterior");

            nextJt.doClick();
            layoutAll(pageJt);
            assertEquals(10, count(pageJt, ChipMa.class), "pagina 2");
            assertTrue(prevJt.isEnabled());

            nextJt.doClick();
            layoutAll(pageJt);
            assertEquals(5, count(pageJt, ChipMa.class), "pagina 3 (los 5 que sobran)");
            assertFalse(nextJt.isEnabled(), "en la ultima pagina no hay siguiente");

            try {
                BufferedImage imgJt = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_RGB);
                Graphics2D gJt = imgJt.createGraphics();
                gJt.setColor(UIManager.getColor("Panel.background"));
                gJt.fillRect(0, 0, 1000, 700);
                prevJt.doClick();
                layoutAll(pageJt);
                pageJt.printAll(gJt);
                gJt.dispose();
                File fileJt = new File("target/ui/paginas.png");
                fileJt.getParentFile().mkdirs();
                ImageIO.write(imgJt, "png", fileJt);
            } catch (Exception exJt) {
                throw new RuntimeException(exJt);
            }
        });
    }

    @Test
    void ConPocosElementosNoHayPaginador() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FakePageJt pageJt = new FakePageJt(7);
            pageJt.setSize(1000, 700);
            layoutAll(pageJt);
            assertEquals(7, count(pageJt, ChipMa.class));
            assertFalse(findButton(pageJt, "Siguiente  ›").getParent().isVisible(), "el paginador se oculta");
        });
    }

    @Test
    void elBotonActualizarTrabajaEnSegundoPlanoYSeBloqueaMientrasTanto() throws Exception {
        FakePageJt[] holderJt = new FakePageJt[1];
        SwingUtilities.invokeAndWait(() -> {
            holderJt[0] = new FakePageJt(12);
            holderJt[0].setSize(1000, 700);
            layoutAll(holderJt[0]);
        });
        FakePageJt pageJt = holderJt[0];
        JButton refreshJt = findByTooltip(pageJt, "Actualizar ahora");
        assertTrue(refreshJt != null && refreshJt.isEnabled());

        SwingUtilities.invokeAndWait(refreshJt::doClick);
        assertTrue(pageJt.startedJt.await(3, TimeUnit.SECONDS), "debe empezar a actualizar");
        // mientras trabaja: boton bloqueado y aviso visible
        boolean[] stateJt = new boolean[2];
        SwingUtilities.invokeAndWait(() -> {
            stateJt[0] = !refreshJt.isEnabled();
            stateJt[1] = hasLabelText(pageJt, "Actualizando…");
        });
        assertTrue(stateJt[0], "el boton queda en espera");
        assertTrue(stateJt[1], "se avisa 'Actualizando…'");

        pageJt.releaseJt.countDown();
        long deadlineJt = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadlineJt && !refreshJt.isEnabled()) {
            Thread.sleep(50);
        }
        assertTrue(refreshJt.isEnabled(), "al terminar vuelve a estar disponible");
        assertTrue(pageJt.refreshedJt.get());
        SwingUtilities.invokeAndWait(() -> assertTrue(hasLabelText(pageJt, "Se encontraron 12 sitios")));
    }

    private static boolean hasLabelText(Container rootJt, String textJt) {
        for (Component childJt : rootJt.getComponents()) {
            if (childJt instanceof JLabel lJt && textJt.equals(lJt.getText())) {
                return true;
            }
            if (childJt instanceof Container cJt && hasLabelText(cJt, textJt)) {
                return true;
            }
        }
        return false;
    }
}
