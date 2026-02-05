package org.iumotionlabs.hefesto.desktop.cache;

import java.time.Duration;

public record CacheKey<T>(String key, Class<T> type, Duration ttl) {

    public static <T> CacheKey<T> of(String key, Class<T> type, Duration ttl) {
        return new CacheKey<>(key, type, ttl);
    }
}
