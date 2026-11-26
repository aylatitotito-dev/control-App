package com.appcontrol.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.appcontrol.BuildInfoMa;
import com.appcontrol.update.UpdateInfoJtMa;
import com.appcontrol.update.UpdateServiceJt;

/**
 * El recorrido de una actualización: se explica qué pasará y se pide confirmar, se descarga con una barra de avance, se
 * comprueba la firma, y al terminar se reinicia AppControl con la versión nueva. Nada se instala si algo no coincide.
 */
final class UpdateFlowJt {

    private UpdateFlowJt() {
    }

    static void startJt(Component parentJt, UpdateServiceJt serviceJt, UpdateInfoJtMa infoJt, Runnable exitJt) {
        if (!serviceJt.canApplyJt()) {
            JOptionPane.showMessageDialog(parentJt, "Esta copia no se instaló con el instalador, así que no puede "
                    + "actualizarse sola.\nInstala el AppControl-0.0.1.exe más nuevo.", "Actualización",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String textJt = "Hay una versión nueva de AppControl (compilación del "
                + BuildInfoMa.localTextMa(infoJt.build()) + ", " + Math.max(1, infoJt.size() / 1048576) + " MB).\n\n"
                + "Se descarga, se comprueba su firma y AppControl se reinicia con la versión nueva. Tus datos y tu cuenta "
                + "se conservan.\nMientras reinicia (unos segundos) no se aplican reglas.\n\n¿Actualizar ahora?";
        if (JOptionPane.showConfirmDialog(parentJt, textJt, "Actualizar AppControl", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        JProgressBar barJt = new JProgressBar(0, 100);
        barJt.setStringPainted(true);
        barJt.setString("Descargando…");
        JLabel labelJt = new JLabel("Descargando la actualización…");
        JPanel panelJt = new JPanel(new BorderLayout(0, 10));
        panelJt.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        panelJt.add(labelJt, BorderLayout.NORTH);
        panelJt.add(barJt, BorderLayout.CENTER);
        JDialog dialogJt = new JDialog(SwingUtilities.getWindowAncestor(parentJt), "Actualizando",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialogJt.setContentPane(panelJt);
        dialogJt.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialogJt.setSize(420, 140);
        dialogJt.setLocationRelativeTo(parentJt);

        SwingWorker<Path, Long> workerJt = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return serviceJt.downloadJt(infoJt, this::publish);
            }

            @Override
            protected void process(java.util.List<Long> chunksJt) {
                long doneJt = chunksJt.get(chunksJt.size() - 1);
                if (infoJt.size() > 0) {
                    int pctJt = (int) Math.min(100, doneJt * 100 / infoJt.size());
                    barJt.setValue(pctJt);
                    barJt.setString(pctJt + " %");
                }
            }

            @Override
            protected void done() {
                dialogJt.dispose();
                try {
                    Path newJarJt = get();
                    serviceJt.applyJt(newJarJt);
                    exitJt.run();
                } catch (InterruptedException exJt) {
                    Thread.currentThread().interrupt();
                } catch (Exception exJt) {
                    Throwable causeJt = exJt.getCause() == null ? exJt : exJt.getCause();
                    JOptionPane.showMessageDialog(parentJt, "No se pudo actualizar: " + causeJt.getMessage()
                            + "\nNo se cambió nada; AppControl sigue como estaba.", "Actualización",
                            JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        workerJt.execute();
        dialogJt.setVisible(true);
    }
}
