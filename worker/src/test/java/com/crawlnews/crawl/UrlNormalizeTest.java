package com.crawlnews.crawl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UrlNormalizeTest {

  @Test
  void forDedup_returnsEmptyForNullOrBlank() {
    assertEquals("", UrlNormalize.forDedup(null));
    assertEquals("", UrlNormalize.forDedup(""));
    assertEquals("", UrlNormalize.forDedup("   "));
  }

  @Test
  void forDedup_keepsSchemeAndHost() {
    assertEquals("https://www.investing.com/", UrlNormalize.forDedup("https://www.investing.com/"));
    assertEquals("http://example.com/a", UrlNormalize.forDedup("http://example.com/a/"));
  }

  @Test
  void forDedup_stripsAccidentalDoubleScheme() {
    assertEquals(
        "https://www.investing.com/",
        UrlNormalize.forDedup("https://https://www.investing.com/"));
    assertEquals(
        "https://www.investing.com/",
        UrlNormalize.forDedup("https://http://www.investing.com/"));
  }

  @Test
  void forDedup_handlesSchemeRelativeUrl() {
    assertEquals("https://example.com/x", UrlNormalize.forDedup("//example.com/x"));
  }

  @Test
  void forDedup_rejectsSchemeNameAsHost() {
    // "https" or "http" alone resolves to host="https"/"http" after prepending. Reject.
    assertEquals("", UrlNormalize.forDedup("https"));
    assertEquals("", UrlNormalize.forDedup("http"));
  }

  @Test
  void forDedup_rejectsNonHttpSchemes() {
    assertEquals("", UrlNormalize.forDedup("ftp://example.com/"));
    assertEquals("", UrlNormalize.forDedup("ws://example.com/"));
    assertEquals("", UrlNormalize.forDedup("javascript:alert(1)"));
    assertEquals("", UrlNormalize.forDedup("mailto:a@b.com"));
    assertEquals("", UrlNormalize.forDedup("data:text/plain,hello"));
  }

  @Test
  void forDedup_prependsHttpsWhenSchemeMissing() {
    assertEquals(
        "https://www.investing.com/", UrlNormalize.forDedup("www.investing.com/"));
  }

  @Test
  void forDedup_dropsTrailingSlashExceptRoot() {
    assertEquals("https://a.example.com/b", UrlNormalize.forDedup("https://a.example.com/b/"));
    assertEquals("https://a.example.com/", UrlNormalize.forDedup("https://a.example.com/"));
  }

  @Test
  void forDedup_lowercasesHost() {
    assertEquals("https://www.example.com/", UrlNormalize.forDedup("https://WWW.Example.COM/"));
  }

  @Test
  void forDedup_preservesQueryString() {
    assertEquals(
        "https://example.com/a?x=1&y=2",
        UrlNormalize.forDedup("https://example.com/a?x=1&y=2"));
  }

  @Test
  void forDedup_sortsQueryParams() {
    assertEquals(
        "https://example.com/a?x=1&y=2",
        UrlNormalize.forDedup("https://example.com/a?y=2&x=1"));
  }

  @Test
  void forDedup_stripsTrackingParams() {
    assertEquals(
        "https://example.com/a?x=1",
        UrlNormalize.forDedup(
            "https://example.com/a?utm_source=news&utm_medium=email&x=1&fbclid=abc"));
  }

  @Test
  void forDedup_allTrackingStrippedYieldsNoQuery() {
    assertEquals(
        "https://example.com/a",
        UrlNormalize.forDedup("https://example.com/a?utm_campaign=x&gclid=y"));
  }

  @Test
  void forDedup_trackingParamNamesAreCaseInsensitive() {
    assertEquals(
        "https://example.com/a",
        UrlNormalize.forDedup("https://example.com/a?UTM_Campaign=x&FBCLID=y"));
  }

  @Test
  void isAllowedHost_matchesExactOrSubdomain() {
    assertTrue(UrlNormalize.isAllowedHost("https://investing.com/", "investing.com"));
    assertTrue(UrlNormalize.isAllowedHost("https://www.investing.com/", "investing.com"));
    assertTrue(UrlNormalize.isAllowedHost("https://vn.investing.com/a", "investing.com"));
  }

  @Test
  void isAllowedHost_rejectsUnrelatedDomains() {
    assertFalse(UrlNormalize.isAllowedHost("https://investing.co.uk/", "investing.com"));
    assertFalse(UrlNormalize.isAllowedHost("https://foo.example.com/", "investing.com"));
    assertFalse(UrlNormalize.isAllowedHost("https://fakeinvesting.com/", "investing.com"));
  }

  @Test
  void isAllowedHost_handlesEdgeCaseSuffixes() {
    assertFalse(UrlNormalize.isAllowedHost("https://www.example.com/", ""));
    assertFalse(UrlNormalize.isAllowedHost("https://www.example.com/", null));
    assertTrue(UrlNormalize.isAllowedHost("https://www.example.com/", ".example.com."));
  }

  @Test
  void looksLikePathTrap_flagsDeepPaths() {
    String deep = "https://example.com/" + "a/".repeat(20);
    assertTrue(UrlNormalize.looksLikePathTrap(deep, 14));
    assertFalse(UrlNormalize.looksLikePathTrap("https://example.com/a/b/c", 14));
  }
}
