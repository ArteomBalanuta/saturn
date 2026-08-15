package org.saturn.app.service.impl;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.saturn.app.agent.AgentConfig;
import org.saturn.app.agent.AgentInvocation;
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
    if (!config.enabled()) {
      reply(invocation, "The agent is disabled.");
      return false;
    }
    if (closed.get()) {
      reply(invocation, "The agent is unavailable because Saturn is shutting down.");
      return false;
    }
    if (!admission.tryAcquire()) {
      reply(invocation, "The agent is busy; try again shortly.");
      return false;
    }

    try {
      executor.submit(() -> execute(invocation));
      return true;
    } catch (RuntimeException exception) {
      admission.release();
      reply(invocation, "The agent could not accept that request.");
      return false;
    }
  }

  private void execute(AgentInvocation invocation) {
    var context = invocation.context();
    log.info(
        "Agent request started, requestId={}, room={}, nick={}",
        invocation.requestId(),
        context.room(),
        context.nick());
    try {
      var result = router.route(invocation);
      log.info(
          "Agent request completed, requestId={}, correlationId={}",
          invocation.requestId(),
          result.correlationId());
      reply(invocation, result.content());
    } catch (AgentRoutingException exception) {
      log.warn(
          "Agent request failed, requestId={}, room={}, nick={}, reason={}",
          invocation.requestId(),
          context.room(),
          context.nick(),
          exception.getMessage());
      log.debug("Agent routing failure, requestId={}", invocation.requestId(), exception);
      reply(invocation, "The agent could not answer that request.");
    } catch (RuntimeException exception) {
      log.error(
          "Unexpected agent routing failure, requestId={}, type={}, message={}",
          invocation.requestId(),
          exception.getClass().getSimpleName(),
          exception.getMessage());
      log.debug(
          "Unexpected agent routing failure, requestId={}", invocation.requestId(), exception);
      reply(invocation, "The agent could not answer that request.");
    } finally {
      admission.release();
    }
  }

  private void reply(AgentInvocation invocation, String content) {
    var context = invocation.context();
    outService.enqueueMessageForSending(context.nick(), content, context.whisper());
    try {
      replyFlusher.run();
    } catch (RuntimeException exception) {
      log.error("Agent reply flush failed, requestId={}", invocation.requestId(), exception);
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      executor.close();
    }
  }
}
