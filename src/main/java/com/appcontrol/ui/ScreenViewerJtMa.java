package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.appcontrol.cloud.CloudClientMa.InputEvMa;
import com.appcontrol.cloud.CloudClientMa.ScreenFrameMa;
import com.appcontrol.cloud.CloudServiceMa;

/**
 * Ventana para ver la pantalla de otro equipo. Además se puede pedir usar su mouse y teclado: la persona decide en su
 * equipo cada vez y puede cortarlo cuando quiera. Pide una imagen nueva cada segundo (cada 0,4 s si se está usando) y
 * al cerrar se avisa a la nube para que el otro equipo deje de compartir. No hay comandos: solo lo que se haría con
 * el mouse y el teclado.
 */
final class ScreenViewerJtMa extends JDialog {

    /** El servidor espera hasta que llegue una imagen nueva, así que se vuelve a preguntar casi enseguida. */
    private static final int POLL_MS_MA = 250;
    private static final int POLL_CONTROL_MS_MA = 60;
    private static final int WAIT_MS_MA = 1500;
    private static final int FLUSH_MS_MA = 50;
    private static final int MAX_FAILS_MA = 5;

    private final CloudServiceMa cloudMa;
    private final long groupIdMa;
    private final long memberIdMa;
    private final JLabel statusMa = new JLabel("Pidiendo la pantalla… la primera imagen puede tardar unos segundos.");
    private final JButton controlButtonMa = new JButton("Tomar el control del mouse y teclado");
    private final FramePanelMa panelMa = new FramePanelMa();
    private final Timer timerMa;
    private final Timer flushTimerMa;
    private final List<InputEvMa> pendingMa = new ArrayList<>();

    private long lastNumberMa;
    private boolean inFlightMa;
    private boolean sendingMa;
    private boolean closedMa;
    private int failsMa;
    /** El dueño pidió control y espera o ya lo tiene. */
    private boolean wantControlMa;
    private boolean controlActiveMa;

    private ScreenViewerJtMa(Window ownerMa, CloudServiceMa cloudMa, long groupIdMa, long memberIdMa,
            String nameMa) {
        super(ownerMa, "Pantalla de " + nameMa, ModalityType.MODELESS);
        this.cloudMa = cloudMa;
        this.groupIdMa = groupIdMa;
        this.memberIdMa = memberIdMa;
        setLayout(new BorderLayout());

        controlButtonMa.addActionListener(evMa -> toggleControlMa());
        JPanel topMa = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        topMa.add(controlButtonMa);
        topMa.add(new JLabel("La persona lo aceptó al aceptar el control y puede cortarlo con «Detener»."));
        statusMa.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        add(topMa, BorderLayout.NORTH);
        add(panelMa, BorderLayout.CENTER);
        add(statusMa, BorderLayout.SOUTH);
        setSize(new Dimension(1100, 740));
        setLocationRelativeTo(ownerMa);

        timerMa = new Timer(POLL_MS_MA, evMa -> pollMa());
        timerMa.setInitialDelay(0);
        flushTimerMa = new Timer(FLUSH_MS_MA, evMa -> flushMa());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evMa) {
                closeMa();
            }

            @Override
            public void windowClosed(WindowEvent evMa) {
                closeMa();
            }
        });
        installInputMa();
    }

    static void openMa(Window ownerMa, CloudServiceMa cloudMa, long groupIdMa, long memberIdMa, String nameMa) {
        ScreenViewerJtMa viewerMa = new ScreenViewerJtMa(ownerMa, cloudMa, groupIdMa, memberIdMa, nameMa);
        viewerMa.setVisible(true);
        viewerMa.timerMa.start();
    }

    // ------------------------------------------------------------------ pantalla

    private void pollMa() {
        if (inFlightMa || closedMa) {
            return;
        }
        inFlightMa = true;
        long sinceMa = lastNumberMa;
        UiAsyncJt.runJt(() -> {
            ScreenFrameMa frameMa = cloudMa.client().watchScreen(groupIdMa, memberIdMa, sinceMa, true, WAIT_MS_MA);
            BufferedImage imageMa = frameMa.jpeg() == null ? null
                    : ImageIO.read(new ByteArrayInputStream(frameMa.jpeg()));
            return new Object[] {imageMa, frameMa.number(), frameMa.input(), frameMa.applying()};
        }, resultMa -> {
            inFlightMa = false;
            failsMa = 0;
            if (closedMa) {
                return;
            }
            if (resultMa[0] != null) {
                lastNumberMa = (Long) resultMa[1];
                panelMa.setImageMa((BufferedImage) resultMa[0]);
            }
            applyInputStateMa((String) resultMa[2], resultMa[0] != null, (Boolean) resultMa[3]);
        }, errorMa -> {
            inFlightMa = false;
            failsMa++;
            statusMa.setText(errorMa);
            if (failsMa >= MAX_FAILS_MA) {
                timerMa.stop();
                statusMa.setText(errorMa + " Cierra y vuelve a abrir para reintentar.");
            }
        });
    }

    // ------------------------------------------------------------------ mouse y teclado

    private void toggleControlMa() {
        if (wantControlMa) {
            releaseControlMa("Soltaste el control. Sigues viendo la pantalla.");
            UiAsyncJt.runJt(() -> {
                cloudMa.client().stopScreenInput(groupIdMa, memberIdMa);
                return Boolean.TRUE;
            }, okMa -> {
            }, errorMa -> statusMa.setText(errorMa));
            return;
        }
        controlButtonMa.setEnabled(false);
        statusMa.setText("Tomando el control…");
        UiAsyncJt.runJt(() -> {
            cloudMa.client().requestScreenInput(groupIdMa, memberIdMa);
            return Boolean.TRUE;
        }, okMa -> {
            wantControlMa = true;
            controlButtonMa.setText("Soltar el control");
            controlButtonMa.setEnabled(true);
        }, errorMa -> {
            controlButtonMa.setEnabled(true);
            statusMa.setText(errorMa);
        });
    }

    private int notApplyingMa;

    private void applyInputStateMa(String inputMa, boolean gotFrameMa, boolean applyingMa) {
        if (wantControlMa && "allowed".equals(inputMa)) {
            if (!controlActiveMa) {
                controlActiveMa = true;
                timerMa.setDelay(POLL_CONTROL_MS_MA);
                flushTimerMa.start();
                panelMa.requestFocusInWindow();
            }
            notApplyingMa = applyingMa ? 0 : notApplyingMa + 1;
            if (notApplyingMa >= 6) {
                statusMa.setText("Su equipo NO está recibiendo el mouse y teclado. Lo más probable: tiene abierta una "
                        + "versión anterior de AppControl. Que cierre AppControl del todo (icono junto al reloj > Salir), "
                        + "instale la versión nueva y la abra otra vez.");
            } else {
                statusMa.setText("Estás usando su mouse y teclado. Haz clic en la imagen y escribe como si estuvieras "
                        + "frente al equipo. La persona puede cortarlo cuando quiera.");
            }
        } else if (wantControlMa && "requested".equals(inputMa)) {
            statusMa.setText("Conectando con su mouse y teclado…");
        } else if (wantControlMa && "none".equals(inputMa)) {
            releaseControlMa("La persona cortó el control del mouse y teclado. Sigues viendo la pantalla.");
        } else if (gotFrameMa && !wantControlMa) {
            statusMa.setText("En vivo (solo vista). La persona ve un aviso mientras miras.");
        }
    }

    private void releaseControlMa(String messageMa) {
        wantControlMa = false;
        controlActiveMa = false;
        flushTimerMa.stop();
        timerMa.setDelay(POLL_MS_MA);
        synchronized (pendingMa) {
            pendingMa.clear();
        }
        controlButtonMa.setText("Tomar el control del mouse y teclado");
        controlButtonMa.setEnabled(true);
        statusMa.setText(messageMa);
    }

    /** Guarda un gesto para mandarlo en el próximo envío. Los movimientos seguidos se funden en el último. */
    private void queueMa(InputEvMa evMa) {
        if (!controlActiveMa) {
            return;
        }
        synchronized (pendingMa) {
            int lastMa = pendingMa.size() - 1;
            if (lastMa >= 0 && "move".equals(evMa.type()) && "move".equals(pendingMa.get(lastMa).type())) {
                pendingMa.set(lastMa, evMa);
            } else if (pendingMa.size() < 90) {
                pendingMa.add(evMa);
            }
        }
    }

    private void flushMa() {
        if (sendingMa || closedMa || !controlActiveMa) {
            return;
        }
        List<InputEvMa> batchMa;
        synchronized (pendingMa) {
            if (pendingMa.isEmpty()) {
                return;
            }
            batchMa = new ArrayList<>(pendingMa);
            pendingMa.clear();
        }
        sendingMa = true;
        UiAsyncJt.runJt(() -> {
            cloudMa.client().sendScreenInput(groupIdMa, memberIdMa, batchMa);
            return Boolean.TRUE;
        }, okMa -> sendingMa = false, errorMa -> {
            sendingMa = false;
            releaseControlMa(errorMa);
        });
    }

    private void installInputMa() {
        MouseAdapter mouseMa = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent evMa) {
                moveMa(evMa);
            }

            @Override
            public void mouseDragged(MouseEvent evMa) {
                moveMa(evMa);
            }

            @Override
            public void mousePressed(MouseEvent evMa) {
                panelMa.requestFocusInWindow();
                buttonMa(evMa, "down");
            }

            @Override
            public void mouseReleased(MouseEvent evMa) {
                buttonMa(evMa, "up");
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent evMa) {
                double[] posMa = panelMa.normalizedMa(evMa.getPoint());
                if (posMa != null) {
                    queueMa(new InputEvMa("wheel", posMa[0], posMa[1], 0, 0, evMa.getWheelRotation()));
                }
            }

            private void moveMa(MouseEvent evMa) {
                double[] posMa = panelMa.normalizedMa(evMa.getPoint());
                if (posMa != null) {
                    queueMa(new InputEvMa("move", posMa[0], posMa[1], 0, 0, 0));
                }
            }

            private void buttonMa(MouseEvent evMa, String typeMa) {
                double[] posMa = panelMa.normalizedMa(evMa.getPoint());
                if (posMa != null && evMa.getButton() >= 1 && evMa.getButton() <= 3) {
                    queueMa(new InputEvMa("move", posMa[0], posMa[1], 0, 0, 0));
                    queueMa(new InputEvMa(typeMa, posMa[0], posMa[1], evMa.getButton(), 0, 0));
                }
            }
        };
        panelMa.addMouseListener(mouseMa);
        panelMa.addMouseMotionListener(mouseMa);
        panelMa.addMouseWheelListener(mouseMa);
        panelMa.setFocusable(true);
        panelMa.setFocusTraversalKeysEnabled(false);
        panelMa.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent evMa) {
                keyMa(evMa, "keydown");
            }

            @Override
            public void keyReleased(KeyEvent evMa) {
                keyMa(evMa, "keyup");
            }

            @Override
            public void keyTyped(KeyEvent evMa) {
                // las teclas se mandan con keyPressed / keyReleased
            }

            private void keyMa(KeyEvent evMa, String typeMa) {
                if (controlActiveMa && evMa.getKeyCode() != KeyEvent.VK_UNDEFINED) {
                    queueMa(new InputEvMa(typeMa, 0, 0, 0, evMa.getKeyCode(), 0));
                    evMa.consume();
                }
            }
        });
    }

    // ------------------------------------------------------------------ cerrar

    private void closeMa() {
        if (closedMa) {
            return;
        }
        closedMa = true;
        timerMa.stop();
        flushTimerMa.stop();
        UiAsyncJt.runJt(() -> {
            cloudMa.client().stopScreen(groupIdMa, memberIdMa);
            return Boolean.TRUE;
        }, okMa -> {
        }, errorMa -> {
        });
        dispose();
    }

    /** La imagen se dibuja entera dentro de la ventana, sin deformarse. */
    private static final class FramePanelMa extends JPanel {
        private BufferedImage imageMa;
        private int drawXMa;
        private int drawYMa;
        private int drawWMa = 1;
        private int drawHMa = 1;

        FramePanelMa() {
            setBackground(new Color(0x11, 0x18, 0x27));
        }

        void setImageMa(BufferedImage imageMa) {
            this.imageMa = imageMa;
            repaint();
        }

        /** Posición del cursor como fracción (0 a 1) de la pantalla de la otra persona; null si está fuera de la imagen. */
        double[] normalizedMa(java.awt.Point pointMa) {
            if (imageMa == null || pointMa.x < drawXMa || pointMa.y < drawYMa || pointMa.x > drawXMa + drawWMa
                    || pointMa.y > drawYMa + drawHMa) {
                return null;
            }
            return new double[] {(pointMa.x - drawXMa) / (double) drawWMa, (pointMa.y - drawYMa) / (double) drawHMa};
        }

        @Override
        protected void paintComponent(Graphics gMa) {
            super.paintComponent(gMa);
            if (imageMa == null) {
                return;
            }
            Graphics2D g2Ma = (Graphics2D) gMa.create();
            g2Ma.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double scaleMa = Math.min(getWidth() / (double) imageMa.getWidth(),
                    getHeight() / (double) imageMa.getHeight());
            drawWMa = Math.max(1, (int) (imageMa.getWidth() * scaleMa));
            drawHMa = Math.max(1, (int) (imageMa.getHeight() * scaleMa));
            drawXMa = (getWidth() - drawWMa) / 2;
            drawYMa = (getHeight() - drawHMa) / 2;
            g2Ma.drawImage(imageMa, drawXMa, drawYMa, drawWMa, drawHMa, null);
            g2Ma.dispose();
        }
    }

    /** Para pruebas: decodifica una imagen como lo hace el visor. */
    static BufferedImage decodeMa(byte[] jpegMa) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpegMa));
    }
}
