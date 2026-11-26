package com.appcontrol;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JOptionPane;

/**
 * Evita abrir dos copias de AppControl a la vez: comparten el puerto y la base de datos, y la segunda fallaba sin
 * explicar nada ("Failed to launch JVM"). Si ya hay otra, se avisa con claridad y se sale sin tocar nada.
 */
public final class SingleInstanceMa {

    /** Se guardan para que el candado dure mientras viva el programa. */
    private static FileChannel channelMa;
    private static FileLock lockMa;
    private static RandomAccessFile fileMa;
    /** Qué hacer cuando otro intento de abrir AppControl pide traer esta ventana al frente. */
    private static volatile Runnable activateMa;
    private static volatile boolean pendingMa;

    private SingleInstanceMa() {
    }

    /** true si esta es la unica copia y puede arrancar; false si ya hay otra (ya se aviso al usuario). */
    static boolean acquireMa(String[] argsMa) {
        int portMa = portOfMa(argsMa);
        boolean lockedByOtherMa = !lockFileMa();
        if (lockedByOtherMa && signalRunningCopyMa()) {
            // Ya estaba abierto: se le pidió que se muestre y esta copia se cierra sin ningún aviso.
            return false;
        }
        if (!lockedByOtherMa && !portBusyMa(portMa)) {
            startListenerMa();
            return true;
        }
        String textMa = "AppControl ya está abierto en este equipo (búscalo junto al reloj, en los iconos ocultos).\n\n"
                + "Si no lo ves, otro programa está usando el puerto " + portMa + " o hay una copia de AppControl "
                + "cerrándose: espera unos segundos e inténtalo de nuevo.";
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println(textMa);
        } else {
            JOptionPane.showMessageDialog(null, textMa, "AppControl", JOptionPane.INFORMATION_MESSAGE);
        }
        return false;
    }

    /** La ventana principal se registra aquí para que "abrir AppControl" desde cualquier acceso la traiga al frente. */
    public static void onActivateMa(Runnable activateMa) {
        SingleInstanceMa.activateMa = activateMa;
        if (pendingMa) {
            pendingMa = false;
            activateMa.run();
        }
    }

    private static Path portFileMa() {
        String baseMa = System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home");
        return Path.of(baseMa, "AppControl", "appcontrol.port");
    }

    /** Escucha solo en 127.0.0.1: otra copia que se intenta abrir le dice "SHOW" y esta muestra su ventana. */
    private static void startListenerMa() {
        try {
            ServerSocket serverMa = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
            Files.writeString(portFileMa(), Integer.toString(serverMa.getLocalPort()));
            Thread threadMa = new Thread(() -> {
                while (!serverMa.isClosed()) {
                    try (Socket clientMa = serverMa.accept()) {
                        clientMa.setSoTimeout(2000);
                        BufferedReader readerMa = new BufferedReader(
                                new InputStreamReader(clientMa.getInputStream(), StandardCharsets.UTF_8));
                        if ("SHOW".equals(readerMa.readLine())) {
                            Runnable runMa = activateMa;
                            if (runMa == null) {
                                pendingMa = true;
                            } else {
                                runMa.run();
                            }
                        }
                    } catch (IOException exMa) {
                        // un intento fallido no debe detener la escucha
                    }
                }
            }, "single-instance");
            threadMa.setDaemon(true);
            threadMa.start();
        } catch (IOException | RuntimeException exMa) {
            // sin esto solo se pierde traer la ventana al frente; la app sigue normal
        }
    }

    /** Pide a la copia que ya está abierta que se muestre. true si ella contestó. */
    private static boolean signalRunningCopyMa() {
        try {
            int portMa = Integer.parseInt(Files.readString(portFileMa()).trim());
            try (Socket socketMa = new Socket()) {
                socketMa.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), portMa), 1500);
                OutputStream outMa = socketMa.getOutputStream();
                outMa.write("SHOW\n".getBytes(StandardCharsets.UTF_8));
                outMa.flush();
                return true;
            }
        } catch (IOException | RuntimeException exMa) {
            return false;
        }
    }

    private static int portOfMa(String[] argsMa) {
        for (String argMa : argsMa) {
            if (argMa.startsWith("--server.port=")) {
                try {
                    return Integer.parseInt(argMa.substring("--server.port=".length()).trim());
                } catch (NumberFormatException exMa) {
                    return 8080;
                }
            }
        }
        return 8080;
    }

    /** Toma el candado del archivo. false si otra copia ya lo tiene. Si no se puede crear el archivo, no estorba. */
    private static boolean lockFileMa() {
        try {
            String baseMa = System.getenv("APPDATA") != null ? System.getenv("APPDATA")
                    : System.getProperty("user.home");
            Path dirMa = Path.of(baseMa, "AppControl");
            Files.createDirectories(dirMa);
            fileMa = new RandomAccessFile(dirMa.resolve("appcontrol.lock").toFile(), "rw");
            channelMa = fileMa.getChannel();
            lockMa = channelMa.tryLock();
            return lockMa != null;
        } catch (IOException | RuntimeException exMa) {
            return true;
        }
    }

    private static boolean portBusyMa(int portMa) {
        try (ServerSocket probeMa = new ServerSocket(portMa, 1, InetAddress.getLoopbackAddress())) {
            return false;
        } catch (IOException exMa) {
            return true;
        }
    }
}
