package com.losant.mqtt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class DeviceRegistryTest {

  @Test
  void shutdownAllIsANoOpWithNoRegisteredDevices() {
    DeviceRegistry registry = new DeviceRegistry();

    assertDoesNotThrow(registry::shutdownAll);
  }

  @Test
  void shutdownAllDisconnectsEveryRegisteredDevice() {
    DeviceRegistry registry = new DeviceRegistry();
    registry.register(new Device("device-1", "key", "secret"));
    registry.register(new Device("device-2", "key", "secret"));

    assertDoesNotThrow(registry::shutdownAll);
  }

  @Test
  void unregisteredDeviceIsNotAffectedByShutdownAll() {
    DeviceRegistry registry = new DeviceRegistry();
    Device device = new Device("device-1", "key", "secret");
    registry.register(device);
    registry.unregister(device);

    assertDoesNotThrow(registry::shutdownAll);
  }

  @Test
  void registerShutdownHookDoesNotThrow() {
    DeviceRegistry registry = new DeviceRegistry();

    assertDoesNotThrow(registry::registerShutdownHook);
  }
}
