package dev.streamshield.browser;

import java.net.IDN;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UrlResolver {
    public static final String INTERNAL_HOME = "jpxfrd:home";
    public static final String SEARCH_ENDPOINT = "https://www.google.com/search?q=";
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern IPV4 = Pattern.compile("[0-9]+(?:\\.[0-9]+){3}");

    private UrlResolver() {}

    public static String resolve(String input) {
        if (input == null || input.isBlank()) return INTERNAL_HOME;
        String value = input.strip();
        if (INTERNAL_HOME.equalsIgnoreCase(value)) return INTERNAL_HOME;

        if (SCHEME.matcher(value).find()) {
            String scheme = value.substring(0, value.indexOf(':')).toLowerCase(Locale.ROOT);
            if (scheme.equals("http") || scheme.equals("https") || scheme.equals("file") || scheme.equals("about")) return value;
            return search(value);
        }

        String hostCandidate = value.split("[/?#]", 2)[0];
        String hostOnly = stripPort(hostCandidate);
        if (looksLikeHost(hostOnly)) {
            String scheme = isLoopback(hostOnly) ? "http://" : "https://";
            return scheme + value;
        }
        return search(value);
    }

    public static boolean isInternalHome(String value) {
        return value == null || value.isBlank() || INTERNAL_HOME.equalsIgnoreCase(value);
    }

    private static String search(String value) {
        return SEARCH_ENDPOINT + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean looksLikeHost(String value) {
        if (value == null || value.isBlank() || value.contains(" ")) return false;
        if (isLoopback(value) || IPV4.matcher(value).matches() || (value.startsWith("[") && value.endsWith("]"))) return true;
        try {
            String ascii = IDN.toASCII(value, IDN.USE_STD3_ASCII_RULES);
            return ascii.contains(".") && !ascii.startsWith(".") && !ascii.endsWith(".");
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String stripPort(String host) {
        if (host.startsWith("[")) {
            int close = host.indexOf(']');
            return close >= 0 ? host.substring(0, close + 1) : host;
        }
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) return host.substring(0, colon);
        return host;
    }

    private static boolean isLoopback(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("localhost") || lower.endsWith(".localhost") || lower.equals("127.0.0.1")
                || lower.startsWith("127.") || lower.equals("[::1]") || lower.equals("::1");
    }
}
