package com.losant.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MQTT client for connecting a device to the Losant IoT Platform.
 *
 * <p>Mirrors the API of the official
 * <a href="https://github.com/Losant/losant-mqtt-js">losant-mqtt-js</a> client: construct with
 * a device ID and Losant access key/secret, register listeners for commands and connection
 * events, then {@link #connect()} and {@link #sendState(Map)}.
 */
public final class Device {

  private static final String DEFAULT_ENDPOINT = "broker.losant.com";

  private final String id;
  private final String key;
  private final String secret;
  private final Transport transport;
  private final String mqttEndpoint;
  private final int mqttPort;
  private final int qosPublish;

  private final ObjectMapper mapper = new ObjectMapper();
  private final CopyOnWriteArrayList<CommandListener> commandListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();

  private MqttClient client;

  public Device(String id, String key, String secret) {
    this(id, key, secret, Transport.TLS, DEFAULT_ENDPOINT, 0);
  }

  public Device(String id, String key, String secret, Transport transport) {
    this(id, key, secret, transport, DEFAULT_ENDPOINT, 0);
  }

  public Device(String id, String key, String secret, Transport transport, String mqttEndpoint, int qosPublish) {
    this(id, key, secret, transport, mqttEndpoint, transport.defaultPort(), qosPublish);
  }

  public Device(String id, String key, String secret, Transport transport, String mqttEndpoint,
      int mqttPort, int qosPublish) {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("id is required");
    }
    if (key == null || secret == null) {
      throw new IllegalArgumentException("key and secret are required");
    }
    if (qosPublish != 0 && qosPublish != 1) {
      throw new IllegalArgumentException("qosPublish must be 0 or 1");
    }
    this.id = id;
    this.key = key;
    this.secret = secret;
    this.transport = transport;
    this.mqttEndpoint = mqttEndpoint;
    this.mqttPort = mqttPort;
    this.qosPublish = qosPublish;
  }

  public void addCommandListener(CommandListener listener) {
    commandListeners.add(listener);
  }

  public void removeCommandListener(CommandListener listener) {
    commandListeners.remove(listener);
  }

  public void addConnectionListener(ConnectionListener listener) {
    connectionListeners.add(listener);
  }

  public void removeConnectionListener(ConnectionListener listener) {
    connectionListeners.remove(listener);
  }

  /**
   * Opens the connection to the Losant broker, subscribes to the device's command topic,
   * and enables automatic reconnection.
   */
  public synchronized void connect() throws MqttException {
    String broker = transport.scheme() + "://" + mqttEndpoint + ":" + mqttPort;

    client = new MqttClient(broker, id, new MemoryPersistence());
    client.setCallback(new MqttCallbackExtended() {
      @Override
      public void connectComplete(boolean reconnect, String serverURI) {
        try {
          client.subscribe(commandTopic(), 0);
        } catch (MqttException e) {
          notifyError(e);
          return;
        }
        if (reconnect) {
          connectionListeners.forEach(ConnectionListener::onReconnect);
        } else {
          connectionListeners.forEach(ConnectionListener::onConnect);
        }
      }

      @Override
      public void connectionLost(Throwable cause) {
        connectionListeners.forEach(ConnectionListener::onClose);
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) {
        handleCommand(message);
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
      }
    });

    MqttConnectOptions options = new MqttConnectOptions();
    options.setUserName(key);
    options.setPassword(secret.toCharArray());
    options.setCleanSession(true);
    options.setAutomaticReconnect(true);
    options.setConnectionTimeout(30);

    client.connect(options);
  }

  /**
   * Closes the connection to the Losant broker.
   */
  public synchronized void disconnect() throws MqttException {
    if (client != null && client.isConnected()) {
      client.disconnect();
    }
  }

  public boolean isConnected() {
    return client != null && client.isConnected();
  }

  /**
   * Publishes a device state report using the current time.
   */
  public void sendState(Map<String, Object> data) throws MqttException {
    sendState(data, Instant.now());
  }

  /**
   * Publishes a device state report.
   */
  public void sendState(Map<String, Object> data, Instant time) throws MqttException {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("data", data);
    payload.put("time", time.toString());

    publish(stateTopic(), payload);
  }

  private void publish(String topic, Object payload) throws MqttException {
    byte[] bytes;
    try {
      bytes = mapper.writeValueAsBytes(payload);
    } catch (Exception e) {
      throw new MqttException(e);
    }
    MqttMessage message = new MqttMessage(bytes);
    message.setQos(qosPublish);
    client.publish(topic, message);
  }

  @SuppressWarnings("unchecked")
  private void handleCommand(MqttMessage message) {
    Map<String, Object> parsed;
    try {
      parsed = mapper.readValue(message.getPayload(), Map.class);
    } catch (Exception e) {
      notifyError(e);
      return;
    }
    Command command = new Command(
        (String) parsed.get("name"),
        (String) parsed.get("time"),
        (Map<String, Object>) parsed.get("payload"));
    commandListeners.forEach(l -> l.onCommand(command));
  }

  private void notifyError(Throwable t) {
    connectionListeners.forEach(l -> l.onError(t));
  }

  private String stateTopic() {
    return "losant/" + id + "/state";
  }

  private String commandTopic() {
    return "losant/" + id + "/command";
  }
}
