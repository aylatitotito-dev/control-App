package com.appcontrol.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import com.appcontrol.cloud.CloudClientMa.CloudExceptionMa;

/** Hace una llamada lenta (la nube) fuera del hilo de la ventana, para que la pantalla nunca se congele. */
final class UiAsyncJt {

    private UiAsyncJt() {
    }

    /** Ejecuta "workJt" en segundo plano y luego, en el hilo de la ventana, "okJt" con el resultado o "failJt" con el texto del error. */
    static <T> void runJt(Callable<T> workJt, Consumer<T> okJt, Consumer<String> failJt) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return workJt.call();
            }

            @Override
            protected void done() {
                try {
                    okJt.accept(get());
                } catch (InterruptedException exJt) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exJt) {
                    Throwable causeJt = exJt.getCause() == null ? exJt : exJt.getCause();
                    failJt.accept(messageJt(causeJt));
                }
            }
        }.execute();
    }

    static String messageJt(Throwable causeJt) {
        if (causeJt instanceof CloudExceptionMa) {
            return causeJt.getMessage();
        }
        return causeJt.getMessage() == null ? causeJt.toString() : causeJt.getMessage();
    }
}
