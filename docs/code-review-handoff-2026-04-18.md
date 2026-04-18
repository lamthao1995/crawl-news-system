# Code Review Handoff - 2026-04-18

Tai lieu nay tong hop cac van de chinh trong codebase hien tai de chuyen cho Claude sua. Muc tieu la uu tien cac loi co anh huong runtime truoc, sau do moi den reliability va tooling.

## Status

**All five findings FIXED on 2026-04-18** (see per-section FIXED notes below). `mvn -B -ntp verify` now passes with 59/59 tests and JaCoCo coverage 90% line / 71% branch (thresholds LINE ≥ 80%, BRANCH ≥ 60% enforced via `jacoco:check`).

## Scope

- Repo: `crawl-news-system`
- Review state: current working tree, khong gia dinh repo sach
- Main focus:
  - `worker/src/main/java`
  - `worker/pom.xml`
  - `docker-compose.yml`
  - test suite hien co

## Priority Summary

1. Fix memory-risk bug in HTTP body handling
2. Fix incorrect Redis URL parsing for auth / DB index / TLS URI details
3. Fix redirect dedup inconsistency when Postgres is disabled or redirect target reappears in-run
4. Validate content type before treating a response as a crawled HTML page
5. Clean up JaCoCo / test tooling noise on current JDK

## Findings

### 1. High - `CRAWL_MAX_BODY_BYTES` does not actually cap response memory usage — **FIXED**

> **FIXED (2026-04-18):** switched to `HttpResponse.BodyHandlers.ofInputStream()` so the response is read as a stream. New static helper `CrawlActivitiesImpl.decodeStream(InputStream, contentEncoding, maxBytes)` reads in 16 KiB chunks, gunzips on the fly when `Content-Encoding: gzip`, and stops at `maxBytes` — the full payload is never allocated. Tests: `CrawlActivitiesBodyTest#decodeStream_capsLiveStream` + existing gzip/plain cap cases. Misleading comment on the helper updated.


**Files**

- `worker/src/main/java/com/crawlnews/worker/activities/CrawlActivitiesImpl.java`

**Problem**

The activity uses:

- `HttpResponse.BodyHandlers.ofByteArray()`

That means the entire HTTP response is already loaded into memory before `decodeBody()` truncates it. So the current `CRAWL_MAX_BODY_BYTES` setting only limits what is parsed afterward, not what is allocated.

Current code/comments imply streaming protection, but runtime behavior does not match that claim.

**Risk**

- Large HTML or binary responses can spike heap usage
- Worker can hit OOM under bad pages or accidental large downloads
- The comment in `decodeBody()` is misleading

**Relevant lines**

- `CrawlActivitiesImpl.java:114`
- `CrawlActivitiesImpl.java:128-129`
- `CrawlActivitiesImpl.java:199-215`

**Suggested fix**

- Switch from `BodyHandlers.ofByteArray()` to a streaming approach
- Enforce byte cap while reading from the response stream
- Keep gzip handling compatible with bounded reads
- Update comment so it matches actual behavior

**Acceptance check**

- A large response should never require loading the full body into heap
- Tests should cover plain and gzip responses over the configured cap

### 2. High - Redis URL parsing ignores credentials, DB index, and URI options — **FIXED**

> **FIXED (2026-04-18):** `buildJedis()` now instantiates `new JedisPooled(URI)` directly. Jedis 5.x's URI constructor honors scheme (`redis` / `rediss`), userinfo (`user:password`), path-based DB index, and supported URI query options — covering `redis://:secret@host:6379/5` and `rediss://user:secret@host:6380/1` without our parsing losing information. The old host/port/scheme-only reconstruction was removed.


**Files**

- `worker/src/main/java/com/crawlnews/worker/activities/CrawlActivitiesImpl.java`

**Problem**

`buildJedis()` reconstructs Redis config from only:

- host
- port
- scheme check for `rediss`

It ignores:

- username
- password
- database index from URI path
- URI query options

So URLs like:

- `redis://:secret@redis:6379/5`
- `rediss://user:secret@redis.example.com:6380/1`

will connect incorrectly or fail auth.

**Risk**

- Breaks non-local deploys using authenticated Redis
- Can silently use DB `0` instead of expected DB
- TLS setup is incomplete for real hosted Redis setups

**Relevant lines**

- `CrawlActivitiesImpl.java:59-68`

**Suggested fix**

- Build Jedis from the full Redis URI, not only host/port
- Preserve auth, DB index, TLS, and supported URI parameters
- Add tests for:
  - unauthenticated local Redis URL
  - password-protected URL
  - non-zero DB index
  - `rediss://`

### 3. Medium - Redirect target is not deduped consistently outside Postgres — **FIXED**

> **FIXED (2026-04-18):** two complementary changes.
>
> - **Activity** (`CrawlActivitiesImpl.fetchWithRedisClaim`): on a successful fetch where `finalUrl != url`, we now mirror the dedup entry into both Postgres (already present) **and** Redis via a `SET NX` claim on `CLAIM_PREFIX + sha(finalUrl)`. This preserves dedup when `CRAWL_JDBC_URL` is unset.
> - **Workflow** (`InvestingSpiralCrawlWorkflowImpl`): after counting a page as fetched, we add `page.getFinalUrl()` to `queuedThisRun`, so a later extracted link pointing at the redirect destination is treated as already-queued and never issued as a second activity call.
>
> Test: new `InvestingSpiralCrawlWorkflowTest#redirectedFinalUrl_isNotRefetchedWhenRediscovered` asserts `callsFor(finalUrl) == 0` when the seed redirects and the resulting page re-links to the destination.


**Files**

- `worker/src/main/java/com/crawlnews/worker/workflows/InvestingSpiralCrawlWorkflowImpl.java`
- `worker/src/main/java/com/crawlnews/worker/activities/CrawlActivitiesImpl.java`

**Problem**

When a URL redirects:

- Redis claim is created for the original canonical URL hash
- Postgres is optionally written for both original and final URL hashes
- Workflow frontier dedup only tracks queued links, not `page.getFinalUrl()`

So if Postgres is disabled, or if final redirected URLs re-enter via discovered links, the crawler can refetch the same destination.

**Risk**

- Duplicate fetches in the same run
- Duplicate fetches across runs when Postgres is intentionally disabled
- Reduced crawl budget efficiency

**Relevant lines**

- `InvestingSpiralCrawlWorkflowImpl.java:47-52`
- `InvestingSpiralCrawlWorkflowImpl.java:121-141`
- `CrawlActivitiesImpl.java:88-93`
- `CrawlActivitiesImpl.java:159-164`

**Suggested fix**

- After a successful fetch, mark `page.getFinalUrl()` in workflow dedup state as well
- Consider claiming both original and final canonical hashes in Redis
- Define desired behavior clearly when redirect source and target both appear in frontier

**Acceptance check**

- A redirected destination should not be fetched twice in one run
- Behavior should still be correct when `CRAWL_JDBC_URL` is unset

### 4. Medium - Non-HTML same-origin responses can be counted as crawled pages — **FIXED**

> **FIXED (2026-04-18):** added a content-type gate before parse/store.
>
> - New helper `CrawlActivitiesImpl.isHtmlLikeContentType(String)` accepts `text/html` and `application/xhtml+xml` (with optional `; charset=...`); a blank `Content-Type` is treated as permissive (many sites omit it). Everything else is rejected.
> - New `PageFetchResult.skippedNonHtml(contentType)` factory + `isSkippedNonHtml()` flag (carries the offending Content-Type in `errorMessage`).
> - New `CrawlSummary.skippedNonHtml` counter; the workflow increments it on non-HTML responses — distinct from `fetchErrors` so "skip" and "error" stay separate in metrics.
>
> Tests: `CrawlActivitiesBodyTest#contentType_*` (3 cases: html-like accepted, non-html rejected, blank permissive), `PageFetchResultTest#skippedNonHtml_*`, and `InvestingSpiralCrawlWorkflowTest#nonHtmlResponse_countsSkippedNonHtml_notError`.


**Files**

- `worker/src/main/java/com/crawlnews/worker/activities/CrawlActivitiesImpl.java`

**Problem**

The request accepts broad content:

- `text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`

But the response is treated as success before validating `Content-Type`. The body is parsed via Jsoup and written to durable dedup even if the URL returns a PDF, image, feed, or other asset.

**Risk**

- Binary/download URLs can consume page budget
- `crawl_url_seen` can be polluted with non-page resources
- Crawl quality drops because "pages fetched" no longer means HTML pages

**Relevant lines**

- `CrawlActivitiesImpl.java:108-114`
- `CrawlActivitiesImpl.java:128-133`
- `CrawlActivitiesImpl.java:159-165`

**Suggested fix**

- Check `Content-Type` before parse/store
- Define allowed types explicitly, for example:
  - `text/html`
  - maybe `application/xhtml+xml`
- Treat unsupported types as skipped or failure, depending on desired metrics semantics
- Add tests for PDF / image / XML behavior

### 5. Medium - JaCoCo emits heavy stacktraces on current JDK even though build passes — **FIXED**

> **FIXED (2026-04-18):** constrained the JaCoCo agent to instrument only our own package via `<includes>com/crawlnews/**/*</includes>` in `worker/pom.xml`. JDK internals / third-party classes compiled for a class-file major version newer than the agent supports are no longer instrumented, eliminating the `IllegalClassFormatException: Unsupported class file major version ...` noise without needing to upgrade the toolchain. `mvn -q -B -ntp verify` runs clean; `jacoco:check` still enforces LINE ≥ 80%, BRANCH ≥ 60%.


**Files**

- `worker/pom.xml`

**Problem**

`mvn test` and `mvn verify` pass, but JaCoCo prints instrumentation stacktraces similar to:

- `IllegalClassFormatException`
- `Unsupported class file major version 69`

This means the test pipeline is not clean on the current JDK/runtime combination.

**Risk**

- Noisy CI logs hide real failures
- Future toolchain upgrades may turn this from warning-noise into hard failure
- Team confidence in test output drops

**Relevant lines**

- `pom.xml:77-126`

**Suggested fix**

- Update JaCoCo/tooling to a version fully compatible with the JDK in use
- Or configure excludes so problematic JDK internals are not instrumented
- Keep `mvn test` and `mvn verify` output clean

**Validation run already observed**

- `mvn -q -B -ntp test` -> passed
- `mvn -q -B -ntp verify` -> passed
- But both emitted JaCoCo stacktraces before tests

## Testing Gaps

Current unit tests are decent for:

- workflow branching
- URL normalization
- body decoding helper
- DTO behavior

But important integration-sensitive areas are still under-covered or excluded:

- `CrawlActivitiesImpl`
- `PostgresUrlDedupStore`
- `RedisDomainCircuitBreaker`
- `WorkerMain`

These are explicitly excluded from JaCoCo coverage checks in `worker/pom.xml`.

## Recommended Execution Order

1. Fix HTTP body streaming and add tests
2. Fix Redis URI handling and add tests
3. Fix redirect dedup semantics and add workflow test coverage
4. Add content-type gate and tests
5. Clean JaCoCo/tooling so local and CI output is readable

## Notes For Claude

- Do not revert unrelated working tree changes
- Review current uncommitted changes before editing shared files
- Prefer small, verifiable commits or patches
- After each fix, run at least:
  - `cd worker && mvn -q -B -ntp test`
- For tooling cleanup, also run:
  - `cd worker && mvn -q -B -ntp verify`

## Quick Reference

- Entry point: `worker/src/main/java/com/crawlnews/worker/WorkerMain.java`
- Main workflow: `worker/src/main/java/com/crawlnews/worker/workflows/InvestingSpiralCrawlWorkflowImpl.java`
- Main activity: `worker/src/main/java/com/crawlnews/worker/activities/CrawlActivitiesImpl.java`
- Dedup DB schema: `crawl-db/init/001_schema.sql`
- Worker build/test config: `worker/pom.xml`
