package com.losant.mqtt;

/**
 * Supported transports for connecting to the Losant MQTT broker.
 */
public enum Transport {

  TCP("tcp", 1883),
  TLS("ssl", 8883),
  WS("ws", 80),
  WSS("wss", 443);

  private final String scheme;
  private final int defaultPort;

  Transport(String scheme, int defaultPort) {
    this.scheme = scheme;
    this.defaultPort = defaultPort;
  }

  public String scheme() {
    return scheme;
  }

  public int defaultPort() {
    return defaultPort;
  }
}
