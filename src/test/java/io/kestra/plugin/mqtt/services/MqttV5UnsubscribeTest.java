package io.kestra.plugin.mqtt.services;

import java.util.ConcurrentModificationException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttV5UnsubscribeTest {
    @Test
    void acceptsOnlyTheListenerRaceInsidePaho() {
        assertTrue(MqttV5Service.isListenerRace(new MqttException(new NullPointerException())));
        assertTrue(MqttV5Service.isListenerRace(new MqttException(new ConcurrentModificationException())));
    }

    @Test
    void rejectsATimeoutALostConnectionAndAnUnknownCause() {
        assertFalse(MqttV5Service.isListenerRace(new MqttException(32000)));
        assertFalse(MqttV5Service.isListenerRace(new MqttException(32109)));
        assertFalse(MqttV5Service.isListenerRace(new MqttException(32104)));
        assertFalse(MqttV5Service.isListenerRace(new MqttException(new IllegalStateException("other"))));
    }
}
