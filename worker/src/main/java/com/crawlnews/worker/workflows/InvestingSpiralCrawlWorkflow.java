package com.crawlnews.worker.workflows;

import com.crawlnews.crawl.dto.CrawlSummary;
import com.crawlnews.crawl.dto.InvestingCrawlInput;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Bounded BFS ("spiral rings") from a seed URL with same-host policy, path-depth traps, in-memory
 * frontier dedup, and Redis {@code SET NX} for cross-run URL dedup.
 */
@WorkflowInterface
public interface InvestingSpiralCrawlWorkflow {

  @WorkflowMethod
  CrawlSummary run(InvestingCrawlInput input);
}
