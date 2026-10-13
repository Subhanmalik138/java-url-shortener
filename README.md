# URL Shortener (Java)

A small, dependency-free URL shortening service in Java: turn long URLs into
compact short codes, count clicks per link, and let links expire via TTL.

## Problem

Every link-sharing flow — marketing campaigns, referral programs, QR codes —
needs short, memorable links with basic analytics. The interesting parts are
not the HTTP plumbing but the decisions underneath: how to mint short codes
that never collide, how to count clicks safely under concurrency, and how to
retire links without a background sweeper.

## Design decisions

- **Monotonic counter + Base62, not random strings.** Each new link takes the
  next value from an `AtomicLong` and encodes it in Base62 (0–9, a–z, A–Z —
  URL-safe, no `+`/`/`/`=` like Base64). Codes are unique by construction, so
  there is no retry-on-collision loop and no database round trip to check.
- **Idempotent shortening.** Shortening a URL that already has a live link
  returns the existing code instead of minting a duplicate. Stale entries for
  expired links are cleaned up as part of the same path.
- **Lazy expiry.** Links carry an `expiresAt` timestamp. Expired links are
  evicted the moment someone touches them (`resolve` returns empty), so no
  scheduled cleanup thread is needed.
- **Injected `java.time.Clock`.** The service takes a `Clock`, which makes TTL
  behavior fully deterministic in tests — the test suite advances a mutable
  clock past the TTL instead of sleeping.
- **Thread-safe store.** Both indexes are `ConcurrentHashMap`s and click
  counts are `AtomicLong`s, so concurrent `shorten`/`resolve` calls behave
  correctly.

## Project layout

```
src/main/java/com/subhanmalik/urlshortener/
  UrlShortener.java      service API: shorten / resolve / clickCount / size
  ShortLink.java         immutable link record + atomic click counter
  Base62.java            base62 encode/decode
  UrlShortenerDemo.java  tiny CLI demo
src/test/java/com/subhanmalik/urlshortener/
  TestRunner.java        self-contained test suite (no framework needed)
```

## Build and run

Requires a JDK 11 or newer (no Maven, no dependencies).

```bash
# compile
javac -d out $(find src/main/java -name '*.java')

# run the demo
java -cp out com.subhanmalik.urlshortener.UrlShortenerDemo https://example.com/a/long/url
```

## Run the tests

```bash
javac -d out $(find src/main/java src/test/java -name '*.java')
java -cp out com.subhanmalik.urlshortener.TestRunner
```

Expected output: 7 passing tests, `passed: 7, failed: 0`.

Covered: shorten→resolve round trip, 10,000 generated codes unique, click
counter increments, TTL expiry enforced and evicted, duplicate URL returns the
existing code, unknown codes resolve empty, invalid input rejected.

## API sketch

```java
UrlShortener service = new UrlShortener();                    // system clock
String code = service.shorten("https://example.com/long");   // default 30-day TTL
String code2 = service.shorten(url, Duration.ofHours(24));    // custom TTL
Optional<String> target = service.resolve(code);             // records a click
long clicks = service.clickCount(code);
```

## Tech stack

- Language: Java 11+
- Concurrency: `java.util.concurrent` (`ConcurrentHashMap`, `AtomicLong`)
- Time: `java.time` (`Clock`, `Duration`, `Instant`)
- Tests: hand-rolled assertion runner (zero dependencies)

## License

MIT — see [LICENSE](LICENSE).
