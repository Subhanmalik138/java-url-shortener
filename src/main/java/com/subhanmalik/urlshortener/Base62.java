package com.subhanmalik.urlshortener;

/**
 * Base62 encoder/decoder. Short codes are compact, URL-safe strings built
 * from 0-9, a-z and A-Z without padding characters that cause trouble in
 * URLs (no +, / or = like Base64).
 */
public final class Base62 {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length();

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder out = new StringBuilder();
        long n = value;
        while (n > 0) {
            out.append(ALPHABET.charAt((int) (n % BASE)));
            n /= BASE;
        }
        return out.reverse().toString();
    }

    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("encoded value must not be null or empty");
        }
        long result = 0;
        for (int i = 0; i < encoded.length(); i++) {
            int digit = ALPHABET.indexOf(encoded.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("not a base62 character: " + encoded.charAt(i));
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
