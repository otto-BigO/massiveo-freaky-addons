package com.otto.cellescanner;

/**
 * Status for a celle (prison cell) read off a sign.
 */
public enum CelleStatus {
    SOLGT,    // "SOLGT!" - sold / occupied
    TIL_SALG  // "TIL SALG!" - for sale / available
}
