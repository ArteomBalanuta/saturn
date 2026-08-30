package org.saturn.app.listener.snapshot;

public interface OnlineSetPayloadParser {
  OnlineSetSnapshot parse(String jsonText) throws PayloadDecodeException;
}
