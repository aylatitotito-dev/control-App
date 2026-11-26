package com.appcontrol.ui;

import java.util.function.IntFunction;
import java.util.function.Supplier;

import javax.swing.Icon;
import javax.swing.JPopupMenu;

/**
 * Una tarjeta de una pantalla de lista: titulo, subtitulo, etiqueta de estado opcional,
 * barra de progreso opcional, icono (segun el tamano pedido) y menu de acciones opcional.
 * "extra" (opcional) agrega las columnas de la vista de otro equipo: tiempo, categoria, limite y boton de accion.
 */
record BrowseItemMa(String title, String subtitle, String chipText, ChipMa.KindJtMa chipKind, Double progress,
        IntFunction<Icon> icon, Supplier<JPopupMenu> menu, RowExtraMa extra) {

    /** Columnas extra de una fila en la vista de lista. */
    record RowExtraMa(String timeText, String categoryText, Double quotaRatio, String quotaLabel, String actionText,
            Runnable action) {
    }

    BrowseItemMa(String title, String subtitle, String chipText, ChipMa.KindJtMa chipKind, Double progress,
            IntFunction<Icon> icon, Supplier<JPopupMenu> menu) {
        this(title, subtitle, chipText, chipKind, progress, icon, menu, null);
    }
}
