package com.losant.mqtt;

import java.util.Map;

/**
 * A command received from the Losant platform on the {@code losant/{deviceId}/command} topic.
 */
public final class Command {

  private final String name;
  private final String time;
  private final Map<String, Object> payload;

  public Command(String name, String time, Map<String, Object> payload) {
    this.name = name;
    this.time = time;
    this.payload = payload;
  }

  public String getName() {
    return name;
  }

  public String getTime() {
    return time;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  @Override
  public String toString() {
    return "Command{name='" + name + "', time='" + time + "', payload=" + payload + '}';
  }
}
