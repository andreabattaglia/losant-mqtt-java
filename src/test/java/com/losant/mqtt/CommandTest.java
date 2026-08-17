package com.losant.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CommandTest {

  @Test
  void exposesNameTimeAndPayload() {
    Command command = new Command("turnOn", "2026-08-17T12:00:00Z", Map.of("brightness", 80));

    assertEquals("turnOn", command.getName());
    assertEquals("2026-08-17T12:00:00Z", command.getTime());
    assertEquals(Map.of("brightness", 80), command.getPayload());
  }

  @Test
  void toStringIncludesAllFields() {
    Command command = new Command("turnOn", "2026-08-17T12:00:00Z", Map.of("brightness", 80));

    String text = command.toString();

    assertTrue(text.contains("turnOn"));
    assertTrue(text.contains("2026-08-17T12:00:00Z"));
    assertTrue(text.contains("brightness"));
  }
}
