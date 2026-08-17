package com.losant.mqtt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TransportTest {

  @Test
  void tcpUsesPlainSchemeAndDefaultMqttPort() {
    assertEquals("tcp", Transport.TCP.scheme());
    assertEquals(1883, Transport.TCP.defaultPort());
  }

  @Test
  void tlsUsesSslSchemeAndDefaultSecurePort() {
    assertEquals("ssl", Transport.TLS.scheme());
    assertEquals(8883, Transport.TLS.defaultPort());
  }

  @Test
  void wsUsesWebSocketSchemeAndDefaultHttpPort() {
    assertEquals("ws", Transport.WS.scheme());
    assertEquals(80, Transport.WS.defaultPort());
  }

  @Test
  void wssUsesSecureWebSocketSchemeAndDefaultHttpsPort() {
    assertEquals("wss", Transport.WSS.scheme());
    assertEquals(443, Transport.WSS.defaultPort());
  }
}
