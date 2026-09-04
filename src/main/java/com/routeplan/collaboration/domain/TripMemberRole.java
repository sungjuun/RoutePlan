package com.routeplan.collaboration.domain;

public enum TripMemberRole {
    OWNER,
    EDITOR,
    VIEWER;

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }
}
