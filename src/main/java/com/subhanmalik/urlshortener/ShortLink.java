package com.subhanmalik.urlshortener;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single shortened link. Mutable click count only; everything else is
 * fixed at creation time.
 */
public final class ShortLink {

    private final String code;
    private final String originalUrl;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final AtomicLong clickCount = new AtomicLong(0);

    public ShortLink(String code, String originalUrl, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String code() {
        return code;
    }

    public String originalUrl() {
        return originalUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public long clickCount() {
        return clickCount.get();
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    long recordClick() {
        return clickCount.incrementAndGet();
    }
}
