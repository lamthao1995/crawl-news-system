package com.crawlnews.crawl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic URL canonicalization for dedup keys (safe to use from workflow code). */
public final class UrlNormalize {

  /** Query params stripped before hashing (marketing / click-trackers). Lowercase keys. */
  private static final Set<String> TRACKING_PARAM_NAMES =
      Set.of(
          "utm_source",
          "utm_medium",
          "utm_campaign",
          "utm_term",
          "utm_content",
          "utm_id",
          "utm_name",
          "fbclid",
          "gclid",
          "gclsrc",
          "mc_cid",
          "mc_eid",
          "igshid",
          "ref",
          "ref_src",
          "yclid",
          "dclid",
          "_ga");

  private UrlNormalize() {}

  public static String forDedup(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String s = raw.trim();
    if (s.matches("(?i)^(javascript|mailto|data):.*")) {
      return "";
    }
    // Scheme-relative URL: "//www.example.com/..."
    if (s.startsWith("//")) {
      s = "https:" + s;
    }
    // Accidental double scheme from copy/paste, e.g. "https://https://www.investing.com/..."
    while (true) {
      String lower = s.toLowerCase(Locale.ROOT);
      if (lower.startsWith("https://https://")) {
        s = "https://" + s.substring("https://https://".length());
        continue;
      }
      if (lower.startsWith("https://http://")) {
        s = "https://" + s.substring("https://http://".length());
        continue;
      }
      if (lower.startsWith("http://https://")) {
        s = "http://" + s.substring("http://https://".length());
        continue;
      }
      if (lower.startsWith("http://http://")) {
        s = "http://" + s.substring("http://http://".length());
        continue;
      }
      break;
    }
    String lower = s.toLowerCase(Locale.ROOT);
    if (lower.startsWith("https://") || lower.startsWith("http://")) {
      // already has http(s):// prefix — keep as-is
    } else if (lower.matches("^[a-z][a-z0-9+.-]*://.*")) {
      // ftp://, ws://, file://, etc. — do not prepend https:// (would produce https://ftp://...)
      return "";
    } else {
      s = "https://" + s;
    }
    try {
      URI u = URI.create(s.replace(" ", "%20")).normalize();
      String scheme = u.getScheme() != null ? u.getScheme().toLowerCase(Locale.ROOT) : "https";
      String host = u.getHost() != null ? u.getHost().toLowerCase(Locale.ROOT) : "";
      if (host.isEmpty()) {
        return "";
      }
      // Raw value "https" / "http" becomes "https://https" — URI "host" is the scheme name, not a site.
      if ("http".equals(host) || "https".equals(host)) {
        return "";
      }
      int port = u.getPort();
      String auth = host;
      if (port > 0 && !isDefaultPort(scheme, port)) {
        auth = host + ":" + port;
      }
      String path = u.getRawPath();
      if (path == null || path.isEmpty()) {
        path = "/";
      }
      Path normalized = Paths.get(path).normalize();
      path = normalized.toString().replace('\\', '/');
      if (!path.startsWith("/")) {
        path = "/" + path;
      }
      if (path.isEmpty()) {
        path = "/";
      }
      while (path.length() > 1 && path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      String canonicalQuery = canonicalizeQuery(u.getRawQuery());
      StringBuilder out = new StringBuilder();
      out.append(scheme).append("://").append(auth).append(path);
      if (!canonicalQuery.isEmpty()) {
        out.append('?').append(canonicalQuery);
      }
      return out.toString();
    } catch (Exception e) {
      return "";
    }
  }

  public static boolean isAllowedHost(String canonicalUrl, String allowedHostSuffix) {
    if (canonicalUrl == null || canonicalUrl.isEmpty()) {
      return false;
    }
    if (allowedHostSuffix == null || allowedHostSuffix.isBlank()) {
      return false;
    }
    try {
      String host = URI.create(canonicalUrl).getHost();
      if (host == null) {
        return false;
      }
      host = host.toLowerCase(Locale.ROOT);
      String suffix = allowedHostSuffix.trim().toLowerCase(Locale.ROOT);
      while (suffix.startsWith(".")) {
        suffix = suffix.substring(1);
      }
      while (suffix.endsWith(".")) {
        suffix = suffix.substring(0, suffix.length() - 1);
      }
      if (suffix.isEmpty()) {
        return false;
      }
      return host.equals(suffix) || host.endsWith("." + suffix);
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean looksLikePathTrap(String canonicalUrl, int maxPathSegments) {
    try {
      String path = URI.create(canonicalUrl).getPath();
      if (path == null || path.isEmpty()) {
        return false;
      }
      int segments = (int) path.chars().filter(ch -> ch == '/').count();
      return segments > maxPathSegments;
    } catch (Exception e) {
      return true;
    }
  }

  private static boolean isDefaultPort(String scheme, int port) {
    return ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
  }

  /** Strips tracking params and sorts remaining params so equivalent URLs produce one dedup key. */
  private static String canonicalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return "";
    }
    List<String> kept = new ArrayList<>();
    for (String pair : rawQuery.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
      String decodedKeyLower =
          java.net.URLDecoder.decode(rawKey, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
      if (TRACKING_PARAM_NAMES.contains(decodedKeyLower)) {
        continue;
      }
      kept.add(pair);
    }
    if (kept.isEmpty()) {
      return "";
    }
    Collections.sort(kept);
    return String.join("&", kept);
  }

}
