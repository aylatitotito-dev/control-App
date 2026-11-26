package com.appcontrol.ui;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.appcontrol.cloud.GroupRuleJtMa;

/**
 * Lo que se muestra cuando una regla la puso otra persona (el dueño del grupo): se ve con su nombre, pero no se
 * puede cambiar ni quitar desde este equipo.
 */
final class GroupLockJtMa {

    private GroupLockJtMa() {
    }

    /** Texto corto para la etiqueta de la tarjeta. */
    static String chipTextJtMa(GroupRuleJtMa ruleJtMa) {
        return "Bloqueado por " + ruleJtMa.getOwnerName();
    }

    /** Menú de solo lectura que explica quién puso la regla. */
    static JPopupMenu infoMenuJtMa(GroupRuleJtMa ruleJtMa) {
        JPopupMenu menuJtMa = new JPopupMenu();
        JMenuItem titleJtMa = new JMenuItem(ruleJtMa.getOwnerName() + " puso esta regla (grupo «"
                + ruleJtMa.getGroupName() + "»): " + ruleJtMa.summaryMa());
        titleJtMa.setEnabled(false);
        JMenuItem whoJtMa = new JMenuItem("Solo " + ruleJtMa.getOwnerName() + " puede cambiarla o quitarla.");
        whoJtMa.setEnabled(false);
        menuJtMa.add(titleJtMa);
        menuJtMa.add(whoJtMa);
        return menuJtMa;
    }
}
