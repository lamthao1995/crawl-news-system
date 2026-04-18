package com.crawlnews.crawl.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class PageFetchResultTest {

  @Test
  void circuitOpen_setsFlagOnly() {
    PageFetchResult r = PageFetchResult.circuitOpen();
    assertTrue(r.isCircuitOpen());
    assertFalse(r.isOk());
    assertFalse(r.isSkippedAsDuplicate());
    assertNull(r.getFinalUrl());
    assertNull(r.getErrorMessage());
    assertTrue(r.getSameOriginCanonicalUrls().isEmpty());
  }

  @Test
  void skippedDuplicate_setsFlagOnly() {
    PageFetchResult r = PageFetchResult.skippedDuplicate();
    assertTrue(r.isSkippedAsDuplicate());
    assertFalse(r.isOk());
    assertFalse(r.isCircuitOpen());
    assertTrue(r.getSameOriginCanonicalUrls().isEmpty());
  }

  @Test
  void failure_carriesMessage() {
    PageFetchResult r = PageFetchResult.failure("http 503");
    assertFalse(r.isOk());
    assertFalse(r.isSkippedAsDuplicate());
    assertFalse(r.isCircuitOpen());
    assertEquals("http 503", r.getErrorMessage());
    assertTrue(r.getSameOriginCanonicalUrls().isEmpty());
  }

  @Test
  void skippedNonHtml_setsFlagAndCarriesContentType() {
    PageFetchResult r = PageFetchResult.skippedNonHtml("application/pdf");
    assertTrue(r.isSkippedNonHtml());
    assertFalse(r.isOk());
    assertFalse(r.isSkippedAsDuplicate());
    assertFalse(r.isCircuitOpen());
    assertEquals("non-html: application/pdf", r.getErrorMessage());
    assertTrue(r.getSameOriginCanonicalUrls().isEmpty());
  }

  @Test
  void skippedNonHtml_nullContentTypeSafe() {
    PageFetchResult r = PageFetchResult.skippedNonHtml(null);
    assertTrue(r.isSkippedNonHtml());
    assertEquals("non-html: ", r.getErrorMessage());
  }

  @Test
  void success_defensivelyCopiesLinks() {
    java.util.ArrayList<String> src = new java.util.ArrayList<>();
    src.add("https://example.com/a");
    PageFetchResult r = PageFetchResult.success("https://example.com/", "Title", src);
    assertTrue(r.isOk());
    assertEquals("https://example.com/", r.getFinalUrl());
    assertEquals("Title", r.getTitle());
    assertEquals(List.of("https://example.com/a"), r.getSameOriginCanonicalUrls());

    // Mutating the source must not affect the result.
    src.add("https://example.com/b");
    assertNotSame(src, r.getSameOriginCanonicalUrls());
    assertEquals(1, r.getSameOriginCanonicalUrls().size());
  }
}
