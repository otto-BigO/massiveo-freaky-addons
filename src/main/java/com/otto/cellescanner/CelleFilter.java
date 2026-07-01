package com.otto.cellescanner;

import java.util.List;

/**
 * Thin accessor for "which celler currently matter" - any celle (SOLGT or
 * TIL_SALG) whose remaining time falls inside [minHours, maxHours], soonest
 * first. The actual list is built once per scan inside CelleScanner (not
 * per render frame); this just hands callers (HUD, ESP) that cached result
 * so both always agree and neither does any filtering/sorting work itself.
 */
public final class CelleFilter {

    private CelleFilter() {
    }

    public static List<Celle> collectUpcoming() {
        return CelleScannerMod.scanner.getUpcoming();
    }
}
