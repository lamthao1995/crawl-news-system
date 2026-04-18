package com.crawlnews.crawl;

import java.io.Serializable;

public final class FrontierItem implements Serializable {

  private static final long serialVersionUID = 1L;

  private final String canonicalUrl;
  private final int depth;

  public FrontierItem(String canonicalUrl, int depth) {
    this.canonicalUrl = canonicalUrl;
    this.depth = depth;
  }

  public String getCanonicalUrl() {
    return canonicalUrl;
  }

  public int getDepth() {
    return depth;
  }
}
