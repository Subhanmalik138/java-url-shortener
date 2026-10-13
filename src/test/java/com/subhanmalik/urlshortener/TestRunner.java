package com.subhanmalik.urlshortener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Self-contained test runner (no test framework dependency).
 * Run: javac ... then java com.subhanmalik.urlshortener.TestRunner
 * Exits 0 when every test passes, 1 otherwise.
 */
public final class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testRoundTrip();
        testBase62UniquenessUnderBulkGeneration();
        testClickCounterIncrements();
        testExpiryEnforcedAndEvicted();
        testDuplicateUrlReturnsExistingCode();
        testUnknownCodeResolvesEmpty();
        testInvalidInputRejected();

        System.out.println();
        System.out.println("passed: " + passed + ", failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRoundTrip() {
        run("shorten -> resolve round trip", () -> {
            UrlShortener s = new UrlShortener();
            String code = s.shorten("https://example.com/articles/java-url-shortener");
            Optional<String> back = s.resolve(code);
            assertTrue(back.isPresent(), "resolve must return the URL");
            assertEquals("https://example.com/articles/java-url-shortener", back.get(), "round-trip URL mismatch");
        });
    }

    private static void testBase62UniquenessUnderBulkGeneration() {
        run("10,000 generated codes are unique and non-empty", () -> {
            UrlShortener s = new UrlShortener();
            Set<String> codes = new HashSet<>();
            for (int i = 0; i < 10_000; i++) {
                String code = s.shorten("https://example.com/page/" + i);
                assertTrue(code != null && !code.isEmpty(), "code must be non-empty");
                assertTrue(codes.add(code), "duplicate code generated: " + code);
            }
            assertEquals(10_000, codes.size(), "expected 10000 unique codes");
        });
    }

    private static void testClickCounterIncrements() {
        run("click counter increments on each resolve", () -> {
            UrlShortener s = new UrlShortener();
            String code = s.shorten("https://example.com/click-me");
            assertEquals(0L, s.clickCount(code), "clicks must start at 0");
            s.resolve(code);
            s.resolve(code);
            s.resolve(code);
            assertEquals(3L, s.clickCount(code), "clicks must be 3 after three resolves");
        });
    }

    private static void testExpiryEnforcedAndEvicted() {
        run("expired links are rejected and evicted", () -> {
            Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
            MutableClock clock = new MutableClock(t0);
            UrlShortener s = new UrlShortener(clock);
            String code = s.shorten("https://example.com/ephemeral", Duration.ofMinutes(5));

            clock.advance(Duration.ofMinutes(4));
            assertTrue(s.resolve(code).isPresent(), "link must still resolve before TTL");

            clock.advance(Duration.ofMinutes(2)); // now past the 5-minute TTL
            assertTrue(s.resolve(code).isEmpty(), "expired link must not resolve");
            assertEquals(0, s.size(), "expired link must be evicted from the store");
        });
    }

    private static void testDuplicateUrlReturnsExistingCode() {
        run("shortening the same URL twice returns the existing code", () -> {
            UrlShortener s = new UrlShortener();
            String first = s.shorten("https://example.com/once");
            String second = s.shorten("https://example.com/once");
            assertEquals(first, second, "duplicate URL must return the same code");
            assertEquals(1, s.size(), "duplicate shorten must not create a second entry");
        });
    }

    private static void testUnknownCodeResolvesEmpty() {
        run("unknown code resolves to empty and reports -1 clicks", () -> {
            UrlShortener s = new UrlShortener();
            assertTrue(s.resolve("nope123").isEmpty(), "unknown code must resolve empty");
            assertEquals(-1L, s.clickCount("nope123"), "unknown code click count must be -1");
        });
    }

    private static void testInvalidInputRejected() {
        run("null/blank URLs and non-positive TTLs are rejected", () -> {
            UrlShortener s = new UrlShortener();
            assertThrows(() -> s.shorten(null), "null URL");
            assertThrows(() -> s.shorten("   "), "blank URL");
            assertThrows(() -> s.shorten("https://example.com/x", Duration.ZERO), "zero TTL");
            assertThrows(() -> s.shorten("https://example.com/x", Duration.ofSeconds(-1)), "negative TTL");
        });
    }

    // ---- minimal assertion helpers ----

    private static void run(String name, Runnable test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (AssertionError e) {
            failed++;
            System.out.println("FAIL " + name + " — " + e.getMessage());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void assertThrows(Runnable action, String what) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException for " + what);
    }

    /** Clock whose instant can be moved forward for TTL tests. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
