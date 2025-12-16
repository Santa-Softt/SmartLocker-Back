package com.smartlockr.shared.utils;

import com.smartlockr.fleet.domain.enums.LockerSize;

public final class LockerLabelUtil {

    private LockerLabelUtil(){}

    /**
     * Genera una etiqueta de locker estandarizada.
     * Formato: TAMAÑO-ÍNDICE (ej. "M-05", "XL-12").
     *
     * @param size  El tamaño del locker.
     * @param index El número secuencial del locker (basado en 1).
     * @return El string de la etiqueta formateada.
     */
    public static String generate(LockerSize size, int index) {
        return String.format("%s-%02d", size.name(), index);
    }
}
