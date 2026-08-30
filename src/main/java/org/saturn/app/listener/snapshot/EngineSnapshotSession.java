package org.saturn.app.listener.snapshot;

import com.moandjiezana.toml.Toml;
import java.util.function.Consumer;
import org.saturn.app.facade.EngineType;
import org.saturn.app.facade.ListenerProfile;
import org.saturn.app.facade.impl.EngineImpl;
import org.saturn.app.listener.Listener;

/** Adapter exposing a temporary engine only through the snapshot session capability. */
public final class EngineSnapshotSession implements DefaultRoomSnapshotCoordinator.Session {
  private final String id;
  private final EngineImpl engine;

  public EngineSnapshotSession(String id, EngineImpl engine, Consumer<String> snapshotSink) {
    this.id = id;
    this.engine = engine;
    engine.registerPayloadListener("onlineSet", new SnapshotListener(snapshotSink));
  }

  public static EngineSnapshotSession create(
      String id,
      Toml config,
      String channel,
      String nick,
      String password,
      Consumer<String> snapshotSink) {
    EngineImpl engine =
        new EngineImpl(null, config, EngineType.LIST_CMD, ListenerProfile.TEMPORARY_ONLINE_SET);
    engine.setChannel(channel);
    engine.setNick(nick);
    engine.setPassword(password);
    return new EngineSnapshotSession(id, engine, snapshotSink);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public void start() {
    engine.start();
  }

  @Override
  public void close() {
    engine.stop();
  }

  @Override
  public void flush() {
    engine.shareMessages();
  }

  @Override
  public void sendRaw(String payload) {
    engine.outService.enqueueRawMessageForSending(payload);
  }

  private final class SnapshotListener implements Listener {
    private final Consumer<String> snapshotSink;

    private SnapshotListener(Consumer<String> snapshotSink) {
      this.snapshotSink = snapshotSink;
    }

    @Override
    public String getListenerName() {
      return "temporaryOnlineSetSnapshot";
    }

    @Override
    public void notify(String message) {
      snapshotSink.accept(message);
    }
  }
}
