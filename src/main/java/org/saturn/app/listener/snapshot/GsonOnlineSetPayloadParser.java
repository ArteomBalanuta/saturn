package org.saturn.app.listener.snapshot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import org.saturn.app.facade.EngineType;
import org.saturn.app.model.dto.User;

public final class GsonOnlineSetPayloadParser implements OnlineSetPayloadParser {
  private final EngineType engineType;
  private final String workflowId;
  private final String channel;
  private final Gson gson;

  public GsonOnlineSetPayloadParser(EngineType engineType, String workflowId, String channel) {
    this(engineType, workflowId, channel, new Gson());
  }

  GsonOnlineSetPayloadParser(EngineType engineType, String workflowId, String channel, Gson gson) {
    this.engineType = engineType;
    this.workflowId = workflowId;
    this.channel = channel;
    this.gson = gson;
  }

  @Override
  public OnlineSetSnapshot parse(String jsonText) throws PayloadDecodeException {
    try {
      JsonElement root = JsonParser.parseString(jsonText);
      if (!root.isJsonObject()) {
        throw invalid("payload must be a JSON object");
      }
      JsonObject object = root.getAsJsonObject();
      JsonElement command = object.get("cmd");
      if (command == null || command.isJsonNull() || !"onlineSet".equals(command.getAsString())) {
        throw invalid("cmd must be onlineSet");
      }
      String field = engineType == EngineType.AGENT ? "nicks" : "users";
      JsonElement value = object.get(field);
      if (value == null || value.isJsonNull() || !value.isJsonArray()) {
        throw invalid("missing or invalid " + field + " array");
      }
      JsonArray array = value.getAsJsonArray();
      List<User> users = new ArrayList<>(array.size());
      for (JsonElement element : array) {
        if (engineType == EngineType.AGENT) {
          if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid("nicks must contain strings");
          }
          users.add(new User(element.getAsString()));
        } else {
          User user = gson.fromJson(element, User.class);
          if (user == null) throw invalid("users must not contain null values");
          users.add(user);
        }
      }
      return new OnlineSetSnapshot(users, engineType == EngineType.AGENT);
    } catch (PayloadDecodeException e) {
      throw e;
    } catch (Exception e) {
      throw new PayloadDecodeException(context() + ": malformed onlineSet payload", e);
    }
  }

  private PayloadDecodeException invalid(String reason) {
    return new PayloadDecodeException(context() + ": " + reason);
  }

  private String context() {
    return "workflow=" + workflowId + ", channel=" + channel;
  }
}
