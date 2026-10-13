package com.subhanmalik.urlshortener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory URL shortener service.
 *
 * <ul>
 *   <li>Short codes are generated from a monotonic counter encoded in Base62,
 *       so codes are unique by construction (no retries, no collision loops).</li>
 *   <li>Shortening the same URL twice returns the existing code instead of
 *       minting a new one.</li>
 *   <li>Each link carries a TTL. Expired links are lazily evicted on access
 *       and are no longer resolvable.</li>
 *   <li>A {@link Clock} is injected so TTL behavior is deterministic in tests.</li>
 * </ul>
 */
public final class UrlShortener {

    /** Default link lifetime: 30 days. */
    public static final Duration DEFAULT_TTL = Duration.ofDays(30);

    private final ConcurrentHashMap<String, ShortLink> codeIndex = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> urlIndex = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    private final Clock clock;

    public UrlShortener() {
        this(Clock.systemUTC());
    }

    public UrlShortener(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Shortens {@code url} with the default TTL. Returns the existing code if
     * the URL was already shortened and the link is still live.
     */
    public String shorten(String url) {
        return shorten(url, DEFAULT_TTL);
    }

    /**
     * Shortens {@code url} with a custom TTL. Returns the existing code if
     * the URL was already shortened and the link is still live.
     *
     * @throws IllegalArgumentException if the URL is null/blank or the TTL is not positive
     */
    public String shorten(String url, Duration ttl) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be null or blank");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        String normalized = url.trim();
        Instant now = clock.instant();

        String existing = urlIndex.get(normalized);
        if (existing != null) {
            ShortLink link = codeIndex.get(existing);
            if (link != null && !link.isExpired(now)) {
                return link.code();
            }
            // Stale entry: drop it and mint a fresh code below.
            codeIndex.remove(existing);
            urlIndex.remove(normalized, existing);
        }

        String code = Base62.encode(sequence.getAndIncrement());
        ShortLink link = new ShortLink(code, normalized, now, now.plus(ttl));
        codeIndex.put(code, link);
        urlIndex.put(normalized, code);
        return code;
    }

    /**
     * Resolves a short code back to its original URL and records one click.
     * Returns {@link Optional#empty()} for unknown or expired codes; expired
     * entries are evicted.
     */
    public Optional<String> resolve(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        ShortLink link = codeIndex.get(code);
        if (link == null) {
            return Optional.empty();
        }
        if (link.isExpired(now)) {
            evict(link);
            return Optional.empty();
        }
        link.recordClick();
        return Optional.of(link.originalUrl());
    }

    /** Number of times the link has been resolved, or -1 for an unknown code. */
    public long clickCount(String code) {
        ShortLink link = codeIndex.get(code);
        return link == null ? -1 : link.clickCount();
    }

    /** Number of currently stored (not yet evicted) links. */
    public int size() {
        return codeIndex.size();
    }

    private void evict(ShortLink link) {
        codeIndex.remove(link.code(), link);
        urlIndex.remove(link.originalUrl(), link.code());
    }
}
