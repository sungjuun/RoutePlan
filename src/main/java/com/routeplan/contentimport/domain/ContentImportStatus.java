package com.routeplan.contentimport.domain;

public enum ContentImportStatus {
    RECEIVED,
    PROCESSING,
    PLACE_MATCHING,
    COMPLETED,
    AWAITING_INPUT,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == AWAITING_INPUT || this == FAILED;
    }
}
