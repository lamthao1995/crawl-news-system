package com.crawlnews.worker.workflows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.crawlnews.crawl.dto.CrawlSummary;
import com.crawlnews.crawl.dto.InvestingCrawlInput;
import com.crawlnews.crawl.dto.PageFetchResult;
import com.crawlnews.worker.activities.FakeCrawlActivities;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the crawl workflow in isolation with a scripted fake {@link
 * com.crawlnews.worker.activities.CrawlActivities}. Uses Temporal's in-memory test environment so
 * {@code Workflow.sleep} is time-skipped and tests run in milliseconds.
 */
final class InvestingSpiralCrawlWorkflowTest {

  private static final String TASK_QUEUE = "spiral-test-queue";

  private TestWorkflowEnvironment env;
  private Worker worker;
  private WorkflowClient client;
  private FakeCrawlActivities fakeActivities;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(InvestingSpiralCrawlWorkflowImpl.class);
    fakeActivities = new FakeCrawlActivities();
    worker.registerActivitiesImplementations(fakeActivities);
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  private InvestingSpiralCrawlWorkflow newWorkflow() {
    return client.newWorkflowStub(
        InvestingSpiralCrawlWorkflow.class,
        WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
  }

  @Test
  void blankSeed_setsIdleReason() {
    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("");
    CrawlSummary summary = newWorkflow().run(in);
    assertEquals("missing_or_blank_seed_url", summary.getIdleReason());
    assertEquals(0, summary.getPagesFetched());
    assertTrue(summary.getFetchedUrls().isEmpty());
  }

  @Test
  void hostNotAllowed_setsIdleReasonWithParsedHost() {
    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("investing.com");
    CrawlSummary summary = newWorkflow().run(in);
    assertNotNull(summary.getIdleReason());
    assertTrue(
        summary.getIdleReason().startsWith("seed_host_not_allowed_for_suffix=investing.com"),
        "unexpected idleReason: " + summary.getIdleReason());
    assertTrue(
        summary.getIdleReason().contains("parsedHost=example.com"),
        "unexpected idleReason: " + summary.getIdleReason());
    assertEquals(0, summary.getPagesFetched());
  }

  @Test
  void happyPath_fetchesSeedAndDiscoveredLinks() {
    fakeActivities.scriptSuccess(
        "https://example.com/",
        "root",
        List.of("https://example.com/a", "https://example.com/b"));
    fakeActivities.scriptSuccess("https://example.com/a", "a", List.of());
    fakeActivities.scriptSuccess("https://example.com/b", "b", List.of());

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);

    CrawlSummary summary = newWorkflow().run(in);
    assertNull(summary.getIdleReason());
    assertEquals(3, summary.getPagesFetched());
    assertEquals(
        List.of("https://example.com/", "https://example.com/a", "https://example.com/b"),
        summary.getFetchedUrls());
  }

  @Test
  void maxPages_capsFetchedCount() {
    fakeActivities.scriptSuccess(
        "https://example.com/",
        "root",
        List.of("https://example.com/a", "https://example.com/b", "https://example.com/c"));
    fakeActivities.scriptSuccess("https://example.com/a", "a", List.of());
    fakeActivities.scriptSuccess("https://example.com/b", "b", List.of());
    fakeActivities.scriptSuccess("https://example.com/c", "c", List.of());

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(2);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(2, summary.getPagesFetched());
    assertEquals(2, summary.getFetchedUrls().size());
  }

  @Test
  void duplicateResponse_countsSkippedDuplicate() {
    fakeActivities.scriptSuccess(
        "https://example.com/",
        "root",
        List.of("https://example.com/a", "https://example.com/b"));
    fakeActivities.scriptDuplicate("https://example.com/a");
    fakeActivities.scriptDuplicate("https://example.com/b");

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(1, summary.getPagesFetched());
    assertEquals(2, summary.getSkippedDuplicate());
  }

  @Test
  void failureResponse_countsAsFetchError_notTrap() {
    fakeActivities.scriptSuccess(
        "https://example.com/", "root", List.of("https://example.com/a"));
    fakeActivities.scriptFailure("https://example.com/a", "http 500");

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(1, summary.getPagesFetched());
    assertEquals(1, summary.getFetchErrors());
    assertEquals(0, summary.getSkippedTrap());
  }

  @Test
  void pathTrap_onExtractedLink_incrementsSkippedTrap() {
    String trapLink = "https://example.com/" + "a/".repeat(40) + "deep";
    fakeActivities.scriptSuccess("https://example.com/", "root", List.of(trapLink));

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);
    in.setMaxPathSegments(6);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(1, summary.getPagesFetched());
    assertEquals(1, summary.getSkippedTrap());
  }

  @Test
  void circuitOpen_transientThenSuccess() {
    fakeActivities.scriptSuccess(
        "https://example.com/", "root", List.of("https://example.com/a"));
    fakeActivities.scriptCircuitThenSuccess("https://example.com/a", 2, List.of());

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);
    in.setCircuitSleepSeconds(5);
    in.setCircuitMaxWaitsPerUrl(5);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(2, summary.getPagesFetched());
    assertEquals(2, summary.getCircuitOpenWaits());
    assertEquals(0, summary.getCircuitGaveUp());
  }

  @Test
  void circuitOpen_givesUpAfterMaxWaits() {
    fakeActivities.scriptSuccess(
        "https://example.com/", "root", List.of("https://example.com/a"));
    fakeActivities.scriptAlwaysCircuitOpen("https://example.com/a");

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);
    in.setCircuitSleepSeconds(5);
    in.setCircuitMaxWaitsPerUrl(3);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(1, summary.getPagesFetched());
    assertEquals(1, summary.getCircuitGaveUp());
    assertTrue(
        summary.getCircuitOpenWaits() >= in.getCircuitMaxWaitsPerUrl(),
        "expected at least " + in.getCircuitMaxWaitsPerUrl() + " waits, got "
            + summary.getCircuitOpenWaits());
  }

  @Test
  void invalidAllowedHostSuffix_fallsBackToInvestingCom() {
    // Workflow's safeSuffix() rejects suffixes containing spaces or slashes and falls back to
    // "investing.com"; since the seed is example.com, this yields the "host not allowed" idleReason
    // reporting the fallback suffix.
    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("bad suffix/with slash");
    CrawlSummary summary = newWorkflow().run(in);
    assertNotNull(summary.getIdleReason());
    assertTrue(
        summary.getIdleReason().startsWith("seed_host_not_allowed_for_suffix=investing.com"),
        "unexpected idleReason: " + summary.getIdleReason());
  }

  @Test
  void nonHtmlResponse_countsSkippedNonHtml_notError() {
    fakeActivities.scriptSuccess(
        "https://example.com/", "root", List.of("https://example.com/file.pdf"));
    fakeActivities.scriptNonHtml("https://example.com/file.pdf", "application/pdf");

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(1, summary.getPagesFetched());
    assertEquals(1, summary.getSkippedNonHtml());
    assertEquals(0, summary.getFetchErrors());
    assertEquals(0, summary.getSkippedTrap());
  }

  @Test
  void redirectedFinalUrl_isNotRefetchedWhenRediscovered() {
    // Seed redirects to "/real"; the page also links to "/real" directly. The workflow must not
    // enqueue "/real" a second time after observing it as the seed's finalUrl.
    fakeActivities.scriptSuccessWithFinalUrl(
        "https://example.com/",
        "https://example.com/real",
        "root-after-redirect",
        List.of("https://example.com/real", "https://example.com/other"));
    fakeActivities.scriptSuccess("https://example.com/other", "other", List.of());
    fakeActivities.scriptDefault(
        PageFetchResult.failure("unscripted; should not be called again"));

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(2);
    in.setMaxPages(10);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(2, summary.getPagesFetched());
    // /real should never be requested as a standalone URL because it was already the seed's
    // redirected final URL — this is the core invariant the dedup fix provides.
    assertEquals(0, fakeActivities.callsFor("https://example.com/real"));
    assertEquals(1, fakeActivities.callsFor("https://example.com/other"));
  }

  @Test
  void maxDepthZero_stopsAfterSeed() {
    fakeActivities.scriptSuccess(
        "https://example.com/",
        "root",
        List.of("https://example.com/a", "https://example.com/b"));
    fakeActivities.scriptDefault(PageFetchResult.success("unused", "", List.of()));

    InvestingCrawlInput in = new InvestingCrawlInput();
    in.setSeedUrl("https://example.com/");
    in.setAllowedHostSuffix("example.com");
    in.setMaxDepth(0);
    in.setMaxPages(10);

    CrawlSummary summary = newWorkflow().run(in);
    assertEquals(1, summary.getPagesFetched());
    assertEquals(0, fakeActivities.callsFor("https://example.com/a"));
  }
}
