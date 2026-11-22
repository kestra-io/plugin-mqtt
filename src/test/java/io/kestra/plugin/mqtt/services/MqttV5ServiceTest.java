package io.kestra.plugin.mqtt.services;

import java.util.List;
import java.util.Map;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttToken;
import org.eclipse.paho.mqttv5.client.internal.Token;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.Publish;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class MqttV5ServiceTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldFailWhenBrokerRejectsPublish() throws Exception {
        var service = serviceWithReasonCode(0x87);

        var exception = assertThrows(IllegalStateException.class, () -> service.publish(runContext(), publish(), new byte[0]));

        assertThat(exception.getMessage(), containsString("MQTT broker rejected publish to topic 'test/topic'"));
        assertThat(exception.getMessage(), containsString("135 (0x87): Not authorized."));
    }

    @Test
    void shouldAcceptSuccessfulPublishReasonCodes() throws Exception {
        assertDoesNotThrow(() -> serviceWithReasonCode(0x00).publish(runContext(), publish(), new byte[0]));
        assertDoesNotThrow(() -> serviceWithReasonCode(0x10).publish(runContext(), publish(), new byte[0]));
    }

    // QoS 0 gets no acknowledgement, so the token completes without reason codes.
    @Test
    void shouldAcceptPublishWithoutAcknowledgement() throws Exception {
        assertDoesNotThrow(() -> serviceWithReasonCode().publish(runContext(), publish(), new byte[0]));
    }

    // Publish closes in a finally, so closing an already dropped client must not replace the publish error.
    @Test
    void shouldCloseQuietlyWhenAlreadyDisconnected() throws Exception {
        var service = new MqttV5Service();
        service.client = new MqttAsyncClient("tcp://localhost:1883", "test-client", new MemoryPersistence());

        assertDoesNotThrow(service::close);
    }

    @Test
    void shouldExtractActualMqttV5Properties() {
        var properties = new MqttProperties();
        properties.setUserProperties(List.of(
            new UserProperty("source", "sensor"),
            new UserProperty("environment", "production")
        ));
        properties.setContentType("application/json");
        properties.setMessageExpiryInterval(60L);
        properties.setPayloadFormat(true);

        var result = MqttV5Service.extractMessageProperties(properties);

        assertEquals("application/json", result.get("contentType"));
        assertEquals(60L, result.get("messageExpiryInterval"));
        assertTrue((Boolean) result.get("payloadFormat"));

        assertEquals(Map.of("source", "sensor", "environment", "production"), result.get("userProperties"));
    }

    @Test
    void shouldReturnEmptyPropertiesWhenNoneAreSet() {
        var result = MqttV5Service.extractMessageProperties(new MqttProperties());
        assertTrue(result.isEmpty());
    }

    private RunContext runContext() {
        return runContextFactory.of(Map.of());
    }

    private Publish publish() {
        return Publish.builder()
            .server(Property.ofValue("tcp://localhost:1883"))
            .clientId(Property.ofValue("test-client"))
            .topic(Property.ofValue("test/topic"))
            .retain(Property.ofValue(false))
            .qos(Property.ofValue(1))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .build();
    }

    private MqttV5Service serviceWithReasonCode(int... reasonCodes) throws MqttException {
        var token = new MqttToken();
        token.internalTok = new CompletedToken(reasonCodes);

        var service = new MqttV5Service();
        service.client = new StubClient(token);
        return service;
    }

    private static final class CompletedToken extends Token {
        private CompletedToken(int... reasonCodes) {
            super("test");
            if (reasonCodes.length > 0) {
                updateReasonCodes(reasonCodes);
            }
            markComplete(null, null);
            notifyComplete();
        }
    }

    private static final class StubClient extends MqttAsyncClient {
        private final IMqttToken token;

        private StubClient(IMqttToken token) throws MqttException {
            super("tcp://localhost:1883", "test-client", new MemoryPersistence());
            this.token = token;
        }

        @Override
        public IMqttToken publish(String topic, MqttMessage message) {
            return token;
        }
    }
}
