package org.saturn.app.service.impl;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentConfig;
import org.saturn.app.agent.AgentInvocation;
import org.saturn.app.agent.AgentInvocationMode;
import org.saturn.app.agent.AgentRouter;
import org.saturn.app.agent.AgentRoutingException;
import org.saturn.app.service.AgentService;

@Slf4j
public final class AgentServiceImpl implements AgentService {
  private final AgentConfig config;
  private final AgentRouter router;
  private final OutService outService;
  private final Runnable replyFlusher;
  private final ExecutorService executor;
  private final Semaphore admission;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final AtomicInteger nextCustomId = new AtomicInteger();
  private final AtomicReference<AgentInvocation> pendingAmbient = new AtomicReference<>();
  private final AtomicBoolean ambientScheduled = new AtomicBoolean();

  public AgentServiceImpl(
      AgentConfig config, AgentRouter router, OutService outService, Runnable replyFlusher) {
    this.config = config;
    this.router = router;
    this.outService = outService;
    this.replyFlusher = Objects.requireNonNull(replyFlusher, "replyFlusher");
    this.executor =
        Executors.newSingleThreadExecutor(Thread.ofVirtual().name("saturn-agent-", 0).factory());
    this.admission = new Semaphore(config.maxConcurrentRequests());
  }

  @Override
  public boolean submit(AgentInvocation invocation) {
    Objects.requireNonNull(invocation, "invocation");
    if (invocation.mode() == AgentInvocationMode.AMBIENT) {
      return submitAmbient(invocation);
    }
    if (!config.enabled()) {
      reply(invocation, "The agent is disabled.");
      return false;
    }
    if (closed.get()) {
      if (invocation.mode() != AgentInvocationMode.MODERATION) {
        reply(invocation, "The agent is unavailable because Saturn is shutting down.");
      }
      return false;
    }
    if (!admission.tryAcquire()) {
      reply(invocation, "The agent is busy; try again shortly.");
      return false;
    }

    try {
      executor.submit(() -> execute(invocation, true));
      return true;
    } catch (RuntimeException exception) {
      admission.release();
      reply(invocation, "The agent could not accept that request.");
      return false;
    }
  }

  private boolean submitAmbient(AgentInvocation invocation) {
    if (!config.enabled() || closed.get()) {
      return false;
    }
    pendingAmbient.set(invocation);
    return scheduleAmbient();
  }

  private boolean scheduleAmbient() {
    if (!ambientScheduled.compareAndSet(false, true)) {
      return true;
    }
    try {
      executor.submit(this::executeNextAmbient);
      return true;
    } catch (RuntimeException exception) {
      ambientScheduled.set(false);
      pendingAmbient.set(null);
      log.debug("Agent could not schedule ambient work", exception);
      return false;
    }
  }

  private void executeNextAmbient() {
    AgentInvocation invocation = pendingAmbient.getAndSet(null);
    if (invocation != null && !closed.get()) {
      execute(invocation, false);
    }
    ambientScheduled.set(false);
    if (pendingAmbient.get() != null && !closed.get()) {
      scheduleAmbient();
    }
  }

  private void execute(AgentInvocation invocation, boolean admitted) {
    var context = invocation.context();
    log.info(
        "Agent request started, requestId={}, mode={}, room={}, nick={}",
        invocation.requestId(),
        invocation.mode(),
        context.room(),
        context.nick());
    int customId = nextCustomId();
    try {
      progress(invocation, "working on it", customId);
      var result = router.route(invocation);
      log.info(
          "Agent request completed, requestId={}, correlationId={}",
          invocation.requestId(),
          result.correlationId());
      if (result.shouldReply()) {
        update(invocation, tagged(invocation, "completed: " + result.content()), customId);
      } else if (invocation.mode() == AgentInvocationMode.MODERATION) {
        replyFlusher.run();
      }
    } catch (AgentRoutingException exception) {
      log.warn(
          "Agent request failed, requestId={}, room={}, nick={}, reason={}",
          invocation.requestId(),
          context.room(),
          context.nick(),
          exception.getMessage());
      log.debug("Agent routing failure, requestId={}", invocation.requestId(), exception);
      replyFailureIfRequired(invocation, customId);
    } catch (RuntimeException exception) {
      log.error(
          "Unexpected agent routing failure, requestId={}, type={}, message={}",
          invocation.requestId(),
          exception.getClass().getSimpleName(),
          exception.getMessage());
      log.debug(
          "Unexpected agent routing failure, requestId={}", invocation.requestId(), exception);
      replyFailureIfRequired(invocation, customId);
    } finally {
      if (admitted) {
        admission.release();
      }
    }
  }

  private void replyIfRequired(AgentInvocation invocation, String content) {
    if (invocation.mode().requiresReply()) {
      reply(invocation, content);
    }
  }

  private void replyFailureIfRequired(AgentInvocation invocation, int customId) {
    if (invocation.mode().requiresReply()) {
      update(
          invocation,
          tagged(invocation, "failed: the agent could not answer that request."),
          customId);
    }
  }

  private void progress(AgentInvocation invocation, String message, int customId) {
    if (invocation.mode().requiresReply()) {
      replyProgress(invocation, tagged(invocation, message), customId);
    }
  }

  private void replyProgress(AgentInvocation invocation, String content, int customId) {
    try {
      outService.enqueueAgentMessage(
          invocation.context().nick(), content, invocation.context().whisper(), customId);
      replyFlusher.run();
    } catch (RuntimeException exception) {
      log.error("Agent progress enqueue failed, requestId={}", invocation.requestId(), exception);
    }
  }

  private void update(AgentInvocation invocation, String content, int customId) {
    try {
      outService.updateAgentMessage("overwrite", content, customId);
      replyFlusher.run();
    } catch (RuntimeException exception) {
      log.error("Agent reply update failed, requestId={}", invocation.requestId(), exception);
    }
  }

  private int nextCustomId() {
    return nextCustomId.updateAndGet(current -> current == Integer.MAX_VALUE ? 1 : current + 1);
  }

  private String tagged(AgentInvocation invocation, String message) {
    return "[agent " + visibleRequestId(invocation.requestId()) + "] " + message;
  }

  private String visibleRequestId(String requestId) {
    return requestId.length() <= 12 ? requestId : requestId.substring(0, 12);
  }

  private void reply(AgentInvocation invocation, String content) {
    var context = invocation.context();
    try {
      outService.enqueueMessageForSending(context.nick(), content, context.whisper());
    } catch (RuntimeException exception) {
      log.error("Agent reply enqueue failed, requestId={}", invocation.requestId(), exception);
      return;
    }
    try {
      replyFlusher.run();
    } catch (RuntimeException exception) {
      log.error("Agent reply flush failed, requestId={}", invocation.requestId(), exception);
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      pendingAmbient.set(null);
      executor.close();
    }
  }
}
