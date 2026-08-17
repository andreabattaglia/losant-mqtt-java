package com.losant.mqtt;

/**
 * Receives commands sent from the Losant platform to a device.
 */
@FunctionalInterface
public interface CommandListener {

  void onCommand(Command command);
}
