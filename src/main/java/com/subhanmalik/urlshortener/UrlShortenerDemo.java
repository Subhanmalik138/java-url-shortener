package com.subhanmalik.urlshortener;

import java.time.Duration;
import java.util.Optional;

/**
 * Tiny CLI demo: shorten a URL, resolve it a few times, print analytics.
 *
 * Usage: java ... UrlShortenerDemo <url>
 */
public final class UrlShortenerDemo {

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "https://example.com/some/long/article-path?ref=demo";
        UrlShortener service = new UrlShortener();

        String code = service.shorten(url, Duration.ofHours(24));
        System.out.println("original : " + url);
        System.out.println("short    : /" + code);

        for (int i = 1; i <= 3; i++) {
            Optional<String> target = service.resolve(code);
            System.out.println("visit " + i + " -> " + target.orElse("<expired or unknown>"));
        }
        System.out.println("clicks   : " + service.clickCount(code));

        System.out.println("unknown code resolves to: " + service.resolve("zzz"));
    }
}
