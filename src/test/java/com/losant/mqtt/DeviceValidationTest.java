package com.losant.mqtt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DeviceValidationTest {

  @Test
  void rejectsNullId() {
    assertThrows(IllegalArgumentException.class, () -> new Device(null, "key", "secret"));
  }

  @Test
  void rejectsEmptyId() {
    assertThrows(IllegalArgumentException.class, () -> new Device("", "key", "secret"));
  }

  @Test
  void rejectsNullKey() {
    assertThrows(IllegalArgumentException.class, () -> new Device("device-1", null, "secret"));
  }

  @Test
  void rejectsNullSecret() {
    assertThrows(IllegalArgumentException.class, () -> new Device("device-1", "key", null));
  }

  @Test
  void rejectsQosPublishOtherThanZeroOrOne() {
    assertThrows(IllegalArgumentException.class,
        () -> new Device("device-1", "key", "secret", Transport.TLS, "broker.losant.com", 2));
  }

  @Test
  void acceptsQosPublishZero() {
    new Device("device-1", "key", "secret", Transport.TLS, "broker.losant.com", 0);
  }

  @Test
  void acceptsQosPublishOne() {
    new Device("device-1", "key", "secret", Transport.TLS, "broker.losant.com", 1);
  }

  @Test
  void isNotConnectedBeforeConnectIsCalled() {
    Device device = new Device("device-1", "key", "secret");

    assertFalse(device.isConnected());
  }

  @Test
  void exposesTheIdItWasConstructedWith() {
    Device device = new Device("device-1", "key", "secret");

    assertEquals("device-1", device.getId());
  }

  @Test
  void disconnectIsANoOpWhenNeverConnected() {
    Device device = new Device("device-1", "key", "secret");

    assertDoesNotThrow(device::disconnect);
  }

  @Test
  void disconnectIsIdempotent() {
    Device device = new Device("device-1", "key", "secret");

    assertDoesNotThrow(device::disconnect);
    assertDoesNotThrow(device::disconnect);
  }
}
