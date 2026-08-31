package com.routeplan.optimization.route.cache;

import java.util.concurrent.atomic.AtomicBoolean;

public final class RouteCacheLease implements AutoCloseable {

    public enum Status { ACQUIRED, CONTENDED, BYPASS }

    private final Status status;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    private RouteCacheLease(Status status, Runnable release) {
        this.status = status;
        this.release = release;
    }

    public static RouteCacheLease acquired(Runnable release) {
        return new RouteCacheLease(Status.ACQUIRED, release);
    }

    public static RouteCacheLease waiting() {
        return new RouteCacheLease(Status.CONTENDED, () -> {});
    }

    public static RouteCacheLease bypass() {
        return new RouteCacheLease(Status.BYPASS, () -> {});
    }

    public Status status() { return status; }
    public boolean acquired() { return status == Status.ACQUIRED; }
    public boolean contended() { return status == Status.CONTENDED; }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }
}
