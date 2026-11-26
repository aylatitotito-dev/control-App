package com.appcontrol.tracker;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Lo que pasa cuando el usuario pulsa "Actualizar": lee ahora mismo los historiales de los navegadores y sus
 * iconos, en lugar de esperar a la lectura automatica (cada 2 minutos). Puede tardar unos segundos.
 */
@Component
public class RefreshCoordinatorMa {

    private final ObjectProvider<BrowserHistoryCollectorJt> historyMa;
    private final ObjectProvider<FaviconCollectorJt> faviconsMa;

    public RefreshCoordinatorMa(ObjectProvider<BrowserHistoryCollectorJt> historyMa,
            ObjectProvider<FaviconCollectorJt> faviconsMa) {
        this.historyMa = historyMa;
        this.faviconsMa = faviconsMa;
    }

    /** Lee historiales nuevos y trae los iconos que falten. Bloquea hasta terminar: llamar fuera del hilo de Swing. */
    public void refreshSitesMa() {
        historyMa.ifAvailable(BrowserHistoryCollectorJt::collectJtMa);
        faviconsMa.ifAvailable(FaviconCollectorJt::importMa);
    }
}
