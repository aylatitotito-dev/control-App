package com.appcontrol.service;

import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Avisos discretos al usuario. La interfaz registra como mostrarlos (globo de la bandeja del sistema). */
@Service
public class NotificationServiceJtMa {

    private static final Logger logMa = LoggerFactory.getLogger(NotificationServiceJtMa.class);

    private volatile BiConsumer<String, String> handlerMa;

    public void setHandlerMa(BiConsumer<String, String> handlerMa) {
        this.handlerMa = handlerMa;
    }

    public void notifyMa(String titleMa, String messageMa) {
        BiConsumer<String, String> currentMa = handlerMa;
        if (currentMa != null) {
            currentMa.accept(titleMa, messageMa);
        } else {
            logMa.info("{}: {}", titleMa, messageMa);
        }
    }
}
