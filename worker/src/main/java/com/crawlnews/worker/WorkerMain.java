package com.crawlnews.worker;

import com.crawlnews.worker.activities.CrawlActivitiesImpl;
import com.crawlnews.worker.activities.DemoActivitiesImpl;
import com.crawlnews.worker.workflows.InvestingSpiralCrawlWorkflowImpl;
import com.crawlnews.worker.workflows.NewsDemoWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public final class WorkerMain {

  static {
    System.setProperty("java.net.preferIPv4Stack", "true");
    System.setProperty("java.net.preferIPv6Addresses", "false");
  }

  public static void main(String[] args) throws InterruptedException {
    String target = env("TEMPORAL_TARGET", "127.0.0.1:7233");
    String namespace = env("TEMPORAL_NAMESPACE", "default");
    String taskQueue = env("TEMPORAL_TASK_QUEUE", "news-task-queue");

    for (int attempt = 1; attempt <= 90; attempt++) {
      WorkflowServiceStubs service = null;
      WorkerFactory factory = null;
      try {
        WorkflowServiceStubsOptions stubOptions =
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                // Dev / Compose frontend is plaintext gRPC; without this some environments get
                // StatusRuntimeException UNKNOWN on GetSystemInfo when connecting by IP.
                .setChannelInitializer(
                    cb -> {
                      if (cb instanceof NettyChannelBuilder netty) {
                        netty.usePlaintext();
                      }
                    })
                .build();
        service = WorkflowServiceStubs.newServiceStubs(stubOptions);
        WorkflowClient client =
            WorkflowClient.newInstance(
                service,
                WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
        factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(taskQueue);
        worker.registerWorkflowImplementationTypes(
            NewsDemoWorkflowImpl.class, InvestingSpiralCrawlWorkflowImpl.class);
        worker.registerActivitiesImplementations(new DemoActivitiesImpl(), new CrawlActivitiesImpl());

        System.err.printf(
            "[crawl-worker] Starting WorkerFactory (target=%s namespace=%s taskQueue=%s)%n",
            target, namespace, taskQueue);
        factory.start();
        System.err.printf(
            "[crawl-worker] Polling Temporal target=%s namespace=%s taskQueue=%s%n",
            target, namespace, taskQueue);
        final WorkerFactory runningFactory = factory;
        final WorkflowServiceStubs runningService = service;
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                      runningFactory.shutdown();
                      runningService.shutdown();
                    }));

        try {
          Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        factory.shutdown();
        service.shutdown();
        return;
      } catch (Throwable t) {
        System.err.printf("Waiting for Temporal (%d/90): %s%n", attempt, t.getMessage());
        shutdownQuietly(factory, service);
        Thread.sleep(2000L);
      }
    }
    System.err.println("Could not start worker: Temporal was not reachable in time (90 attempts).");
    System.exit(1);
  }

  private static String env(String key, String defaultValue) {
    String v = System.getenv(key);
    return v == null || v.isBlank() ? defaultValue : v;
  }

  private static void shutdownQuietly(WorkerFactory factory, WorkflowServiceStubs service) {
    if (factory != null) {
      factory.shutdown();
    }
    if (service != null) {
      service.shutdown();
    }
  }
}
