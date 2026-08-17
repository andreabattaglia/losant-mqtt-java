package com.losant.mqtt;

/**
 * Receives lifecycle events for a {@link Device}'s connection to the Losant broker.
 * All methods have empty default implementations so implementors only override what they need.
 */
public interface ConnectionListener {

  default void onConnect() {
  }

  default void onReconnect() {
  }

  default void onClose() {
  }

  default void onError(Throwable error) {
  }
}
