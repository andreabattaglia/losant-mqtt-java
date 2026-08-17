package com.losant.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Exercises {@link Device} against a real MQTT broker (Eclipse Mosquitto) started on demand via
 * Testcontainers, so the suite needs nothing pre-provisioned beyond a running Docker daemon.
 */
@Testcontainers
class DeviceMqttIntegrationTest {

  @Container
  private static final GenericContainer<?> BROKER = new GenericContainer<>(
      DockerImageName.parse("eclipse-mosquitto:2"))
      .withExposedPorts(1883)
      .withCopyFileToContainer(
          MountableFile.forClasspathResource("mosquitto.conf"), "/mosquitto/config/mosquitto.conf")
      .waitingFor(Wait.forListeningPort());

  private static String brokerHost;
  private static int brokerPort;

  private Device device;
  private MqttClient rawClient;

  @BeforeAll
  static void resolveBrokerAddress() {
    brokerHost = BROKER.getHost();
    brokerPort = BROKER.getMappedPort(1883);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (device != null && device.isConnected()) {
      device.disconnect();
    }
    if (rawClient != null) {
      if (rawClient.isConnected()) {
        rawClient.disconnect();
      }
      rawClient.close();
    }
  }

  @Test
  @Timeout(15)
  void connectNotifiesConnectionListenerAndReportsConnected() throws Exception {
    device = newDevice("device-connect", 0);
    CountDownLatch connected = new CountDownLatch(1);
    device.addConnectionListener(new ConnectionListener() {
      @Override
      public void onConnect() {
        connected.countDown();
      }
    });

    device.connect();

    assertTrue(connected.await(10, TimeUnit.SECONDS), "onConnect was not fired");
    assertTrue(device.isConnected());
  }

  @Test
  @Timeout(15)
  void sendStatePublishesDataAndTimeToStateTopic() throws Exception {
    device = newDevice("device-state", 0);
    BlockingQueue<MqttMessage> received = new LinkedBlockingQueue<>();
    rawClient = rawSubscriber("losant/device-state/state", received, 0);

    connectAndAwaitReady(device);
    device.sendState(Map.of("temperature", 21.5));

    MqttMessage message = received.poll(10, TimeUnit.SECONDS);
    assertNotNull(message, "expected a message on the device state topic");

    Map<?, ?> payload = new ObjectMapper().readValue(message.getPayload(), Map.class);
    assertEquals(Map.of("temperature", 21.5), payload.get("data"));
    assertNotNull(payload.get("time"));
  }

  @Test
  @Timeout(15)
  void commandOnCommandTopicNotifiesCommandListener() throws Exception {
    device = newDevice("device-command", 0);
    BlockingQueue<Command> receivedCommands = new LinkedBlockingQueue<>();
    device.addCommandListener(receivedCommands::add);

    connectAndAwaitReady(device);
    publish("losant/device-command/command",
        "{\"name\":\"turnOn\",\"time\":\"2026-08-17T12:00:00Z\",\"payload\":{\"brightness\":80}}");

    Command command = receivedCommands.poll(10, TimeUnit.SECONDS);
    assertNotNull(command, "expected the command listener to be notified");
    assertEquals("turnOn", command.getName());
    assertEquals("2026-08-17T12:00:00Z", command.getTime());
    assertEquals(80, command.getPayload().get("brightness"));
  }

  @Test
  @Timeout(15)
  void qosPublishIsAppliedToPublishedMessages() throws Exception {
    device = newDevice("device-qos", 1);
    BlockingQueue<MqttMessage> received = new LinkedBlockingQueue<>();
    rawClient = rawSubscriber("losant/device-qos/state", received, 1);

    connectAndAwaitReady(device);
    device.sendState(Map.of("ok", true));

    MqttMessage message = received.poll(10, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals(1, message.getQos());
  }

  @Test
  @Timeout(15)
  void disconnectStopsReportingAsConnected() throws Exception {
    device = newDevice("device-disconnect", 0);
    connectAndAwaitReady(device);

    device.disconnect();

    assertFalse(device.isConnected());
  }

  @Test
  @Timeout(15)
  void deviceRegistryShutsDownAllRegisteredDevicesTogether() throws Exception {
    Device deviceA = newDevice("device-registry-a", 0);
    Device deviceB = newDevice("device-registry-b", 0);
    connectAndAwaitReady(deviceA);
    connectAndAwaitReady(deviceB);

    DeviceRegistry registry = new DeviceRegistry();
    registry.register(deviceA);
    registry.register(deviceB);
    registry.shutdownAll();

    assertFalse(deviceA.isConnected());
    assertFalse(deviceB.isConnected());
  }

  private Device newDevice(String id, int qosPublish) {
    return new Device(id, "key", "secret", Transport.TCP, brokerHost, brokerPort, qosPublish);
  }

  /**
   * Waits for {@code onConnect}, which {@link Device} only fires after its command-topic
   * subscription has been acknowledged by the broker, so callers can safely publish/subscribe
   * against the device's topics right after this returns.
   */
  private void connectAndAwaitReady(Device device) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    device.addConnectionListener(new ConnectionListener() {
      @Override
      public void onConnect() {
        latch.countDown();
      }
    });
    device.connect();
    assertTrue(latch.await(10, TimeUnit.SECONDS), "device did not report connected in time");
  }

  private MqttClient rawSubscriber(String topic, BlockingQueue<MqttMessage> queue, int qos) throws Exception {
    MqttClient client = new MqttClient(brokerUri(), MqttClient.generateClientId(), new MemoryPersistence());
    client.setCallback(new MqttCallback() {
      @Override
      public void connectionLost(Throwable cause) {
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) {
        queue.offer(message);
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
      }
    });
    MqttConnectOptions options = new MqttConnectOptions();
    options.setCleanSession(true);
    client.connect(options);
    client.subscribe(topic, qos);
    return client;
  }

  private void publish(String topic, String jsonPayload) throws Exception {
    try (MqttClient publisher = new MqttClient(brokerUri(), MqttClient.generateClientId(), new MemoryPersistence())) {
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      publisher.connect(options);
      publisher.publish(topic, jsonPayload.getBytes(), 0, false);
      publisher.disconnect();
    }
  }

  private String brokerUri() {
    return "tcp://" + brokerHost + ":" + brokerPort;
  }
}
