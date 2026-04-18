package com.crawlnews.worker.workflows;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.crawlnews.worker.activities.DemoActivitiesImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** In-memory Temporal test for the simplest demo workflow (no external services). */
final class NewsDemoWorkflowTest {

  private static final String TASK_QUEUE = "news-demo-test-queue";

  private TestWorkflowEnvironment env;
  private Worker worker;
  private WorkflowClient client;

  @BeforeEach
  void setUp() {
    env = TestWorkflowEnvironment.newInstance();
    worker = env.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(NewsDemoWorkflowImpl.class);
    worker.registerActivitiesImplementations(new DemoActivitiesImpl());
    env.start();
    client = env.getWorkflowClient();
  }

  @AfterEach
  void tearDown() {
    env.close();
  }

  @Test
  void runDemo_echoesInput() {
    NewsDemoWorkflow wf =
        client.newWorkflowStub(
            NewsDemoWorkflow.class, WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    assertEquals("ok:ci", wf.runDemo("ci"));
  }
}
