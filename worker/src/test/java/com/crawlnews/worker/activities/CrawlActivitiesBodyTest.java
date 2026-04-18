package com.crawlnews.worker.activities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** Unit tests for the package-private {@link CrawlActivitiesImpl#decodeBody} helper. */
final class CrawlActivitiesBodyTest {

  private static byte[] gzip(byte[] src) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
      gz.write(src);
    }
    return out.toByteArray();
  }

  @Test
  void plainBody_returnedAsIsUnderCap() throws Exception {
    byte[] src = "<html>hello</html>".getBytes(StandardCharsets.UTF_8);
    byte[] out = CrawlActivitiesImpl.decodeBody(src, "", 4096);
    assertArrayEquals(src, out);
  }

  @Test
  void plainBody_truncatedWhenOverCap() throws Exception {
    byte[] src = new byte[2048];
    for (int i = 0; i < src.length; i++) src[i] = (byte) (i & 0xff);
    byte[] out = CrawlActivitiesImpl.decodeBody(src, "identity", 1024);
    assertEquals(1024, out.length);
    for (int i = 0; i < 1024; i++) {
      assertEquals(src[i], out[i]);
    }
  }

  @Test
  void gzip_isDecompressed() throws Exception {
    byte[] src = "<html>gzipped body with text</html>".getBytes(StandardCharsets.UTF_8);
    byte[] compressed = gzip(src);
    byte[] out = CrawlActivitiesImpl.decodeBody(compressed, "gzip", 4096);
    assertArrayEquals(src, out);
  }

  @Test
  void gzip_capRespected() throws Exception {
    byte[] src = new byte[8192];
    for (int i = 0; i < src.length; i++) src[i] = 'A';
    byte[] compressed = gzip(src);
    byte[] out = CrawlActivitiesImpl.decodeBody(compressed, "GZIP", 1024);
    assertEquals(1024, out.length);
    for (byte b : out) {
      assertEquals('A', b);
    }
  }

  @Test
  void nullBody_returnsEmpty() throws Exception {
    byte[] out = CrawlActivitiesImpl.decodeBody(null, "gzip", 1024);
    assertEquals(0, out.length);
  }

  @Test
  void contentEncodingCaseInsensitive() throws Exception {
    byte[] src = "hi".getBytes(StandardCharsets.UTF_8);
    byte[] compressed = gzip(src);
    assertArrayEquals(src, CrawlActivitiesImpl.decodeBody(compressed, "gzip", 1024));
    assertArrayEquals(src, CrawlActivitiesImpl.decodeBody(compressed, "GZIP", 1024));
    assertArrayEquals(src, CrawlActivitiesImpl.decodeBody(compressed, "Gzip", 1024));
  }

  @Test
  void unknownEncoding_treatedAsPlain() throws Exception {
    byte[] src = "text".getBytes(StandardCharsets.UTF_8);
    byte[] out = CrawlActivitiesImpl.decodeBody(src, "br", 1024);
    assertArrayEquals(src, out);
    assertTrue(out.length <= 1024);
  }

  @Test
  void contentType_htmlLikeAccepted() {
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType("text/html"));
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType("text/html; charset=utf-8"));
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType("TEXT/HTML"));
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType("application/xhtml+xml"));
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType("application/xhtml+xml; charset=UTF-8"));
  }

  @Test
  void contentType_nonHtmlRejected() {
    assertFalse(CrawlActivitiesImpl.isHtmlLikeContentType("application/pdf"));
    assertFalse(CrawlActivitiesImpl.isHtmlLikeContentType("image/jpeg"));
    assertFalse(CrawlActivitiesImpl.isHtmlLikeContentType("application/json"));
    assertFalse(CrawlActivitiesImpl.isHtmlLikeContentType("application/rss+xml"));
    assertFalse(CrawlActivitiesImpl.isHtmlLikeContentType("text/plain"));
  }

  @Test
  void contentType_blankIsPermissive() {
    // Some servers omit Content-Type; treat as HTML so we do not over-skip.
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType(""));
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType(null));
    assertTrue(CrawlActivitiesImpl.isHtmlLikeContentType("   "));
  }

  @Test
  void decodeStream_capsLiveStream() throws Exception {
    // Ensure the streaming path also caps (used in production from HttpResponse body).
    byte[] src = new byte[8192];
    java.util.Arrays.fill(src, (byte) 'Z');
    byte[] out =
        CrawlActivitiesImpl.decodeStream(
            new java.io.ByteArrayInputStream(src), "identity", 2048);
    assertEquals(2048, out.length);
    for (byte b : out) {
      assertEquals('Z', b);
    }
  }
}
