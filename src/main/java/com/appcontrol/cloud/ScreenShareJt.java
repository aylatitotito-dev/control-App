package com.appcontrol.cloud;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.appcontrol.cloud.CloudClientMa.InputEvMa;
import com.appcontrol.cloud.CloudClientMa.ScreenStateMa;

/**
 * Comparte la pantalla de ESTE equipo, pero solo cuando alguien que ya tiene el control aceptado la está mirando
 * (miniatura chica si solo ve el cuadrito, grande si abrió la ventana). El mouse y el teclado también quedaron
 * aceptados al aceptar el control, así que no se vuelve a preguntar; mientras se comparte hay un aviso siempre visible
 * arriba y, si usan el mouse y teclado, trae un botón «Detener» que corta al instante. Nada se guarda.
 */
@Component
public class ScreenShareJt {

    private static final Logger logJt = LoggerFactory.getLogger(ScreenShareJt.class);
    private static final int MAX_WIDTH_JT = 1280;
    private static final float QUALITY_JT = 0.55f;
    private static final int THUMB_WIDTH_JT = 480;
    private static final long THUMB_EVERY_MS_JT = 2000;
    private static final long FRAME_EVERY_MS_JT = 1200;
    private static final long FRAME_EVERY_CONTROL_MS_JT = 120;
    private static final int CONTROL_WIDTH_JT = 960;
    private static final float CONTROL_QUALITY_JT = 0.5f;
    private static final long INPUT_WAIT_MS_JT = 800;
    private static final int MAX_FAILS_JT = 3;

    private final CloudClientMa clientJt;
    private final CloudServiceMa cloudJt;
    private final AtomicBoolean runningJt = new AtomicBoolean(false);
    private final ExecutorService poolJt = daemonPoolJt("screen-share");
    private final ExecutorService inputPoolJt = daemonPoolJt("screen-input");
    private volatile JWindow bannerJt;
    private volatile JLabel bannerLabelJt;
    private volatile JButton stopButtonJt;
    private volatile String sharingWithJt = "";
    private volatile boolean controllingJt;

    private static volatile ScreenShareJt instanceJt;

    public ScreenShareJt(CloudClientMa clientJt, CloudServiceMa cloudJt) {
        this.clientJt = clientJt;
        this.cloudJt = cloudJt;
        instanceJt = this;
    }

    /** Quién está viendo esta pantalla ahora mismo (vacío si nadie). Lo usa la pantalla del grupo. */
    public static String viewerNowJt() {
        ScreenShareJt nowJt = instanceJt;
        return nowJt == null ? "" : nowJt.sharingWithJt;
    }

    /** ¿Están usando mi mouse y teclado ahora mismo? */
    public static boolean controllingNowJt() {
        ScreenShareJt nowJt = instanceJt;
        return nowJt != null && nowJt.controllingJt;
    }

    /** «Detener»: corta el mouse y teclado remotos al instante. */
    public static void cutInputNowJt() {
        ScreenShareJt nowJt = instanceJt;
        if (nowJt != null) {
            nowJt.cutInputJt();
        }
    }

    private void cutInputJt() {
        controllingJt = false;
        inputPoolJt.execute(() -> {
            try {
                clientJt.answerScreenInput(false);
            } catch (RuntimeException exJt) {
                logJt.debug("No se pudo cortar: {}", exJt.toString());
            }
        });
    }

    private static ExecutorService daemonPoolJt(String nameJt) {
        return Executors.newSingleThreadExecutor(runJt -> {
            Thread threadJt = new Thread(runJt, nameJt);
            threadJt.setDaemon(true);
            return threadJt;
        });
    }

    /** Quien está viendo esta pantalla ahora (vacío si nadie). */
    public String sharingWithJt() {
        return sharingWithJt;
    }

    /** Pregunta con poca frecuencia si alguien quiere mirar; solo si este equipo está controlado por otra persona. */
    @Scheduled(initialDelay = 30_000, fixedDelay = 8_000)
    public void checkJt() {
        if (GraphicsEnvironment.isHeadless() || !clientJt.hasToken() || cloudJt.controlledByMa().isEmpty()
                || !runningJt.compareAndSet(false, true)) {
            return;
        }
        poolJt.execute(() -> {
            try {
                shareWhileWantedJt();
            } catch (RuntimeException exJt) {
                logJt.debug("Pantalla compartida: {}", exJt.toString());
            } finally {
                controllingJt = false;
                hideBannerJt();
                sharingWithJt = "";
                runningJt.set(false);
            }
        });
    }

    private void shareWhileWantedJt() {
        String viewerJt = clientJt.screenViewer();
        if (viewerJt.isEmpty()) {
            return;
        }
        Robot robotJt;
        try {
            robotJt = new Robot();
        } catch (AWTException exJt) {
            return;
        }
        sharingWithJt = viewerJt;
        showBannerJt(viewerJt);
        int failsJt = 0;
        boolean pullingJt = false;
        boolean fullJt = false;
        byte[] lastJt = null;
        long lastPutJt = 0;
        while (failsJt < MAX_FAILS_JT && !cloudJt.controlledByMa().isEmpty()) {
            long startJt = System.currentTimeMillis();
            try {
                int widthJt = controllingJt ? CONTROL_WIDTH_JT : fullJt ? MAX_WIDTH_JT : THUMB_WIDTH_JT;
                byte[] jpegJt = captureJt(robotJt, widthJt, controllingJt ? CONTROL_QUALITY_JT : QUALITY_JT);
                if (controllingJt && lastJt != null && java.util.Arrays.equals(lastJt, jpegJt)
                        && System.currentTimeMillis() - lastPutJt < 1000) {
                    Thread.sleep(FRAME_EVERY_CONTROL_MS_JT);
                    continue;
                }
                lastJt = jpegJt;
                lastPutJt = System.currentTimeMillis();
                ScreenStateMa stateJt = clientJt.putScreen(jpegJt);
                fullJt = stateJt.full();
                if (!stateJt.wanted()) {
                    return;
                }
                failsJt = 0;
                if (stateJt.inputAllowed() && !pullingJt) {
                    pullingJt = true;
                    controllingJt = true;
                    setBannerModeJt(viewerJt, true);
                    inputPoolJt.execute(() -> applyInputLoopJt(robotJt));
                } else if (!stateJt.inputAllowed() && pullingJt) {
                    pullingJt = false;
                    controllingJt = false;
                    setBannerModeJt(viewerJt, false);
                }
            } catch (RuntimeException | IOException exJt) {
                failsJt++;
            } catch (InterruptedException exJt) {
                Thread.currentThread().interrupt();
                return;
            }
            long everyJt = controllingJt ? FRAME_EVERY_CONTROL_MS_JT : fullJt ? FRAME_EVERY_MS_JT : THUMB_EVERY_MS_JT;
            long waitJt = everyJt - (System.currentTimeMillis() - startJt);
            if (waitJt > 0) {
                try {
                    Thread.sleep(waitJt);
                } catch (InterruptedException exJt) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ mouse y teclado remotos

    /** Aplica los gestos que manda el dueño mientras la persona lo permita. Al terminar suelta todo lo que quedó apretado. */
    private void applyInputLoopJt(Robot robotJt) {
        Set<Integer> keysDownJt = new HashSet<>();
        Set<Integer> buttonsDownJt = new HashSet<>();
        try {
            while (controllingJt) {
                List<InputEvMa> eventsJt;
                try {
                    eventsJt = clientJt.pullScreenInput(INPUT_WAIT_MS_JT);
                } catch (RuntimeException exJt) {
                    // un corte breve de internet no debe cortar el control: se reintenta enseguida
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException interruptedJt) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                Dimension sizeJt = Toolkit.getDefaultToolkit().getScreenSize();
                for (InputEvMa evJt : eventsJt) {
                    applyJt(robotJt, evJt, sizeJt, keysDownJt, buttonsDownJt);
                }
            }
        } catch (RuntimeException exJt) {
            logJt.debug("Mouse y teclado remotos: {}", exJt.toString());
        } finally {
            for (int codeJt : keysDownJt) {
                robotJt.keyRelease(codeJt);
            }
            for (int buttonJt : buttonsDownJt) {
                robotJt.mouseRelease(InputEvent.getMaskForButton(buttonJt));
            }
        }
    }

    private static void applyJt(Robot robotJt, InputEvMa evJt, Dimension sizeJt, Set<Integer> keysDownJt,
            Set<Integer> buttonsDownJt) {
        try {
            switch (evJt.type()) {
                case "move" -> robotJt.mouseMove((int) (evJt.x() * (sizeJt.width - 1)),
                        (int) (evJt.y() * (sizeJt.height - 1)));
                case "down" -> {
                    if (evJt.button() >= 1 && evJt.button() <= 3) {
                        robotJt.mousePress(InputEvent.getMaskForButton(evJt.button()));
                        buttonsDownJt.add(evJt.button());
                    }
                }
                case "up" -> {
                    if (evJt.button() >= 1 && evJt.button() <= 3) {
                        robotJt.mouseRelease(InputEvent.getMaskForButton(evJt.button()));
                        buttonsDownJt.remove(evJt.button());
                    }
                }
                case "wheel" -> robotJt.mouseWheel(evJt.delta());
                case "keydown" -> {
                    robotJt.keyPress(evJt.code());
                    keysDownJt.add(evJt.code());
                }
                case "keyup" -> {
                    robotJt.keyRelease(evJt.code());
                    keysDownJt.remove(evJt.code());
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException exJt) {
            // tecla que este teclado no tiene: se ignora
        }
    }

    // ------------------------------------------------------------------ captura

    private static byte[] captureJt(Robot robotJt, int maxWidthJt, float qualityJt) throws IOException {
        Dimension sizeJt = Toolkit.getDefaultToolkit().getScreenSize();
        BufferedImage fullJt = robotJt.createScreenCapture(new Rectangle(sizeJt));
        BufferedImage useJt = fullJt;
        if (fullJt.getWidth() > maxWidthJt) {
            int heightJt = fullJt.getHeight() * maxWidthJt / fullJt.getWidth();
            useJt = new BufferedImage(maxWidthJt, heightJt, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2Jt = useJt.createGraphics();
            g2Jt.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2Jt.drawImage(fullJt, 0, 0, maxWidthJt, heightJt, null);
            g2Jt.dispose();
        }
        return jpegJt(useJt, qualityJt);
    }

    static byte[] jpegJt(BufferedImage imageJt) throws IOException {
        return jpegJt(imageJt, QUALITY_JT);
    }

    static byte[] jpegJt(BufferedImage imageJt, float qualityJt) throws IOException {
        ImageWriter writerJt = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam paramJt = writerJt.getDefaultWriteParam();
        paramJt.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        paramJt.setCompressionQuality(qualityJt);
        ByteArrayOutputStream outJt = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream streamJt = new MemoryCacheImageOutputStream(outJt)) {
            writerJt.setOutput(streamJt);
            writerJt.write(null, new IIOImage(imageJt, null, null), paramJt);
        } finally {
            writerJt.dispose();
        }
        return outJt.toByteArray();
    }

    // ------------------------------------------------------------------ aviso siempre visible

    private void showBannerJt(String viewerJt) {
        SwingUtilities.invokeLater(() -> {
            JWindow windowJt = new JWindow();
            windowJt.setAlwaysOnTop(true);
            windowJt.setFocusableWindowState(false);
            JLabel labelJt = new JLabel();
            labelJt.setForeground(Color.WHITE);
            labelJt.setFont(labelJt.getFont().deriveFont(java.awt.Font.BOLD, 13f));
            JButton stopJt = new JButton("Detener");
            stopJt.setFocusable(false);
            stopJt.setVisible(false);
            stopJt.addActionListener(evJt -> {
                controllingJt = false;
                inputPoolJt.execute(() -> {
                    try {
                        clientJt.answerScreenInput(false);
                    } catch (RuntimeException exJt) {
                        logJt.debug("No se pudo cortar: {}", exJt.toString());
                    }
                });
            });
            JPanel panelJt = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
            panelJt.setBackground(new Color(0xC2, 0x41, 0x0C));
            panelJt.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            panelJt.add(labelJt);
            panelJt.add(stopJt);
            windowJt.setContentPane(panelJt);
            bannerLabelJt = labelJt;
            stopButtonJt = stopJt;
            bannerJt = windowJt;
            paintBannerJt(viewerJt, false);
            windowJt.setVisible(true);
        });
    }

    private void setBannerModeJt(String viewerJt, boolean controlJt) {
        SwingUtilities.invokeLater(() -> paintBannerJt(viewerJt, controlJt));
    }

    private void paintBannerJt(String viewerJt, boolean controlJt) {
        JWindow windowJt = bannerJt;
        if (windowJt == null) {
            return;
        }
        bannerLabelJt.setText(controlJt ? "●  " + viewerJt + " está usando tu mouse y teclado"
                : "●  " + viewerJt + " está viendo tu pantalla");
        stopButtonJt.setVisible(controlJt);
        windowJt.pack();
        Dimension screenJt = Toolkit.getDefaultToolkit().getScreenSize();
        windowJt.setLocation((screenJt.width - windowJt.getWidth()) / 2, 0);
    }

    private void hideBannerJt() {
        SwingUtilities.invokeLater(() -> {
            JWindow windowJt = bannerJt;
            bannerJt = null;
            if (windowJt != null) {
                windowJt.dispose();
            }
        });
    }
}
