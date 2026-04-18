-- Durable URL dedup: same SHA-256 hex (64 chars) as Redis keys in the worker (canonical URL bytes).
CREATE TABLE IF NOT EXISTS crawl_url_seen (
    url_sha256      CHAR(64) PRIMARY KEY,
    canonical_url   TEXT        NOT NULL,
    final_url       TEXT,
    title           TEXT,
    content_sha256  CHAR(64),
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_crawl_url_seen_first_seen
    ON crawl_url_seen (first_seen_at DESC);

COMMENT ON TABLE crawl_url_seen IS 'Long-lived dedup; Redis TTL is only a coordination / hot-cache window.';
