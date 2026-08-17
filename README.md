# losant-mqtt-java

Java MQTT client for connecting devices to the [Losant](https://www.losant.com) IoT Platform's
MQTT broker (`broker.losant.com`). Built on [Eclipse Paho](https://www.eclipse.org/paho/) and
modeled after the official [losant-mqtt-js](https://github.com/Losant/losant-mqtt-js) client.

There is no official Losant Java SDK — this is a standalone client that speaks the same
`losant/{deviceId}/state` / `losant/{deviceId}/command` protocol described in the
[Losant MQTT docs](https://docs.losant.com/mqtt/overview/).

## Requirements

- Java 11+
- A device registered on the Losant Platform
- A Losant Access Key / Secret with permission to connect that device

## Install

Build locally with Maven:

```
mvn install
```

## Usage

```java
import com.losant.mqtt.Device;

import java.util.Map;

Device device = new Device("YOUR_DEVICE_ID", "YOUR_ACCESS_KEY", "YOUR_ACCESS_SECRET");

device.addConnectionListener(new ConnectionListener() {
  @Override
  public void onConnect() {
    System.out.println("connected");
  }
});

device.addCommandListener(command ->
    System.out.println("received command: " + command.getName() + " " + command.getPayload()));

device.connect();

device.sendState(Map.of("temperature", 72.4));
```

## API

### `new Device(id, key, secret)`
### `new Device(id, key, secret, transport)`
### `new Device(id, key, secret, transport, mqttEndpoint, qosPublish)`
### `new Device(id, key, secret, transport, mqttEndpoint, mqttPort, qosPublish)`

Creates a client for the given device.

- `id` — Losant Device ID (used as the MQTT client ID)
- `key` / `secret` — Losant Access Key / Secret (used as MQTT username / password)
- `transport` — one of `Transport.TLS` (default, port 8883), `Transport.TCP` (1883),
  `Transport.WS` (80), `Transport.WSS` (443)
- `mqttEndpoint` — broker hostname, defaults to `broker.losant.com`
- `mqttPort` — broker port, defaults to the `transport`'s standard port; only needed to point at
  a non-standard port (e.g. a local/test broker)
- `qosPublish` — QoS used for `sendState` publishes, `0` (default) or `1`. Losant only supports
  QoS 0 for subscriptions (commands), regardless of this setting.

### `connect()`

Opens the connection, subscribes to the device's command topic, and enables automatic
reconnection.

### `disconnect()`

Closes the connection and releases the client's resources. Safe to call more than once.

### `isConnected()`

Returns whether the client is currently connected.

### `getId()`

Returns the Losant Device ID this client was constructed with.

### `sendState(Map<String, Object> data)` / `sendState(Map<String, Object> data, Instant time)`

Publishes a device state report to `losant/{id}/state`.

### `addCommandListener(CommandListener)` / `removeCommandListener(CommandListener)`

Registers/removes a listener invoked for each `Command` received on `losant/{id}/command`.

### `addConnectionListener(ConnectionListener)` / `removeConnectionListener(ConnectionListener)`

Registers/removes a listener for `onConnect`, `onReconnect`, `onClose`, and `onError` events.

## Managing multiple devices

Paho does not pool MQTT connections — each `Device` owns exactly one underlying client. An
application connecting several devices from the same process should track them itself and shut
them all down together, e.g. on JVM exit. `DeviceRegistry` does this:

```java
DeviceRegistry registry = new DeviceRegistry();
registry.register(deviceA);
registry.register(deviceB);
registry.registerShutdownHook(); // disconnects every registered device on JVM shutdown
```

`shutdownAll()` can also be called directly (e.g. from application shutdown logic) instead of
relying on the JVM shutdown hook.

## Limits

Per the Losant broker:

- Subscriptions (commands) are QoS 0 only
- Publishes (state) support QoS 0 or 1
- Retained messages are not supported
- `CleanSession=0` is not supported
- Max payload size is 256KB
- TLS 1.2/1.3 only, requires the DigiCert Global Root G2/G5 CA certificates

## Development

```
mvn clean verify
```

runs the full test suite (unit tests plus a Testcontainers-based MQTT integration suite that
starts a disposable [Eclipse Mosquitto](https://mosquitto.org/) broker — no external broker or
manual setup needed, only a running Docker daemon), collects JaCoCo coverage, and runs an OWASP
Dependency-Check CVE scan (`code-analysis/dependency-check-report.html`) that fails the build on
CVSS ≥ 7 findings. The scan uses the NVD API (set an `nvd.api.key` property in your local
`~/.m2/settings.xml` for reliable/fast lookups — the build works without one, just slower).

All plugin/dependency versions are declared as properties at the top of `pom.xml` — never
hardcoded inline in a `<dependency>`/`<plugin>` block.

## License

MIT
