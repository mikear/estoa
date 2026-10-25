package com.orion.sources;

import java.time.Duration;
import java.util.function.Supplier;

/** Cache simple en memoria con expiracion TTL para feeds voluminosos. */
public class TtlCache<T> {

    private final Duration ttl;
    private volatile long fetchedAt = 0;
    private volatile T value;

    public TtlCache(Duration ttl) { this.ttl = ttl; }

    public synchronized T get(Supplier<T> loader) {
        long now = System.currentTimeMillis();
        if (value == null || now - fetchedAt > ttl.toMillis()) {
            value = loader.get();
            fetchedAt = now;
        }
        return value;
    }

    public synchronized void invalidate() {
        value = null;
        fetchedAt = 0;
    }
}
