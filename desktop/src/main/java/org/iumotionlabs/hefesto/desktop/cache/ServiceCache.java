package org.iumotionlabs.hefesto.desktop.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceCache {

    private static final ServiceCache INSTANCE = new ServiceCache();

    private record CacheEntry(Object value, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private ServiceCache() {}

    public static ServiceCache getInstance() { return INSTANCE; }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(CacheKey<T> key) {
        var entry = cache.get(key.key());
        if (entry == null || entry.isExpired()) {
            cache.remove(key.key());
            return Optional.empty();
        }
        return Optional.of((T) entry.value());
    }

    public <T> void put(CacheKey<T> key, T value) {
        cache.put(key.key(), new CacheEntry(value, Instant.now().plus(key.ttl())));
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public void invalidateAll() {
        cache.clear();
    }

    public <T> T getOrCompute(CacheKey<T> key, java.util.function.Supplier<T> supplier) {
        return get(key).orElseGet(() -> {
            T value = supplier.get();
            put(key, value);
            return value;
        });
    }
}
