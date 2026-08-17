package com.losant.mqtt;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates the graceful shutdown of multiple {@link Device} connections.
 *
 * <p>Paho does not pool MQTT connections: every {@link Device} owns exactly one underlying
 * client. This registry acts as a facade over a set of devices so an application managing
 * several of them can disconnect all of them together, e.g. from a JVM shutdown hook.
 */
public final class DeviceRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(DeviceRegistry.class);

  private final Set<Device> devices = new CopyOnWriteArraySet<>();

  public void register(Device device) {
    devices.add(device);
  }

  public void unregister(Device device) {
    devices.remove(device);
  }

  /**
   * Disconnects and releases every registered device. Failures on individual devices are logged
   * and do not prevent the remaining devices from being shut down.
   */
  public void shutdownAll() {
    for (Device device : devices) {
      try {
        device.disconnect();
      } catch (MqttException e) {
        LOG.warn("Failed to gracefully disconnect device {}", device.getId(), e);
      }
    }
  }

  /**
   * Registers a JVM shutdown hook that calls {@link #shutdownAll()} when the application exits.
   */
  public void registerShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownAll, "device-registry-shutdown"));
  }
}
