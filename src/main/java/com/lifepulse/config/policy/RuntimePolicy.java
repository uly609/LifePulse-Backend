package com.lifepulse.config.policy;

import java.util.concurrent.atomic.AtomicReference;

/** Runtime policy values are sourced from Nacos when it is available. */
public final class RuntimePolicy {
    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(new Snapshot(600, true));
    private RuntimePolicy() { }
    public static Snapshot current() { return CURRENT.get(); }
    public static void replace(Snapshot snapshot) { CURRENT.set(snapshot); }
    public record Snapshot(long shopCacheTtlSeconds, boolean seckillEnabled) { }
}
