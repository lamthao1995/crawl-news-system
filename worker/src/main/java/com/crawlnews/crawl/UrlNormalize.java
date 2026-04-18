package com.crawlnews.crawl;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/** Deterministic URL canonicalization for dedup keys (safe to use from workflow code). */
public final class UrlNormalize {

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
      String query = u.getRawQuery();
      StringBuilder out = new StringBuilder();
      out.append(scheme).append("://").append(auth).append(path);
      if (query != null && !query.isEmpty()) {
        out.append('?').append(query);
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
}
