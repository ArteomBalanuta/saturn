package org.saturn.app.listener.snapshot;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/** Owns one temporary session from start through close; callback concurrency is out of scope. */
@Slf4j
public final class DefaultRoomSnapshotCoordinator implements RoomSnapshotCoordinator {
  public interface Session {
    String id();

    void start() throws Exception;

    void close() throws Exception;

    void flush() throws Exception;

    void sendRaw(String payload) throws Exception;
  }

  @FunctionalInterface
  public interface SessionFactory {
    Session create(RoomSnapshotRequest request, Consumer<String> snapshotSink) throws Exception;
  }

  @FunctionalInterface
  public interface ReplySink {
    void accept(RoomSnapshotRequest request, String reply);
  }

  private final SessionFactory sessionFactory;
  private final ReplySink replySink;
  private final OnlineSetPayloadParser parserFactory;
  private final Duration timeout;
  private final ScheduledExecutorService timer =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "snapshot-workflow-timeout");
            thread.setDaemon(true);
            return thread;
          });
  private final Map<String, Workflow> workflows = new HashMap<>();

  public DefaultRoomSnapshotCoordinator(
      SessionFactory sessionFactory, ReplySink replySink, OnlineSetPayloadParser parserFactory) {
    this(sessionFactory, replySink, parserFactory, Duration.ofSeconds(30));
  }

  public DefaultRoomSnapshotCoordinator(
      SessionFactory sessionFactory,
      ReplySink replySink,
      OnlineSetPayloadParser parserFactory,
      Duration timeout) {
    this.sessionFactory = Objects.requireNonNull(sessionFactory);
    this.replySink = Objects.requireNonNull(replySink);
    this.parserFactory = Objects.requireNonNull(parserFactory);
    this.timeout = Objects.requireNonNull(timeout);
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("timeout must be positive");
  }

  @Override
  public void submit(RoomSnapshotRequest request) {
    Objects.requireNonNull(request);
    Workflow workflow = new Workflow(request);
    workflows.put(request.workflowId(), workflow);
    try {
      workflow.session = sessionFactory.create(request, workflow::receive);
      workflow.session.start();
      timer.schedule(
          () -> workflow.fail(new IllegalStateException("snapshot workflow timed out")),
          timeout.toMillis(),
          java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (Exception error) {
      workflow.fail(error);
    }
  }

  @Override
  public void onSnapshot(String sessionId, String jsonText) {
    Workflow workflow = find(sessionId);
    if (workflow != null) workflow.receive(jsonText);
  }

  @Override
  public void onTransportError(String sessionId, Throwable error) {
    Workflow workflow = find(sessionId);
    if (workflow != null) workflow.fail(error);
  }

  @Override
  public void onClosed(String sessionId, int code, String reason) {
    Workflow workflow = find(sessionId);
    if (workflow != null && !workflow.terminal) workflow.fail(new IllegalStateException(reason));
  }

  @Override
  public void cancel(String workflowId, String reason) {
    Workflow workflow = workflows.get(workflowId);
    if (workflow != null) workflow.fail(new IllegalStateException(reason));
  }

  public int activeWorkflowCount() {
    return workflows.size();
  }

  private Workflow find(String sessionId) {
    return workflows.values().stream()
        .filter(workflow -> workflow.session != null && sessionId.equals(workflow.session.id()))
        .findFirst()
        .orElse(null);
  }

  private final class Workflow {
    private final RoomSnapshotRequest request;
    private Session session;
    private boolean terminal;

    private Workflow(RoomSnapshotRequest request) {
      this.request = request;
    }

    private void receive(String jsonText) {
      if (terminal) {
        log.debug("Ignoring late snapshot for workflow {}", request.workflowId());
        return;
      }
      try {
        OnlineSetSnapshot snapshot = parserFactory.parse(jsonText);
        terminal = true;
        RoomSnapshotContext context =
            new RoomSnapshotContext(
                request.workflowId(),
                request.author(),
                request.sourceChannel(),
                request.targetChannel(),
                request.destinationChannel(),
                reply -> replySink.accept(request, reply),
                payload -> send(payload));
        OperationResult result = request.operation().apply(context, snapshot);
        if (result.reply() != null) replySink.accept(request, result.reply());
        session.flush();
      } catch (Exception error) {
        terminal = true;
        log.error("Snapshot workflow {} failed", request.workflowId(), error);
        publishFailure();
      } finally {
        closeAndRemove();
      }
    }

    private void fail(Throwable error) {
      if (terminal) return;
      terminal = true;
      log.error("Snapshot workflow {} failed", request.workflowId(), error);
      publishFailure();
      closeAndRemove();
    }

    private void publishFailure() {
      if (request.replyMessage() != null) {
        replySink.accept(request, "Unable to complete room operation.");
      }
    }

    private void send(String payload) {
      try {
        session.sendRaw(payload);
      } catch (Exception error) {
        throw new RuntimeException(error);
      }
    }

    private void closeAndRemove() {
      try {
        if (session != null) session.close();
      } catch (Exception closeError) {
        log.error("Failed closing snapshot workflow {}", request.workflowId(), closeError);
      } finally {
        workflows.remove(request.workflowId());
      }
    }
  }
}
