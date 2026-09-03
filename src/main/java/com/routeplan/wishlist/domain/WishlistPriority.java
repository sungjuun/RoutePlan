package com.routeplan.wishlist.domain;

public enum WishlistPriority {
    MUST(100, true),
    HIGH(80, false),
    NORMAL(50, false),
    LOW(20, false);

    private final int tripPriority;
    private final boolean mustVisit;

    WishlistPriority(int tripPriority, boolean mustVisit) {
        this.tripPriority = tripPriority;
        this.mustVisit = mustVisit;
    }

    public int tripPriority() {
        return tripPriority;
    }

    public boolean mustVisit() {
        return mustVisit;
    }
}
