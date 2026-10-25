package io.kestra.plugin.mqtt.services;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.Publish;
import io.kestra.plugin.mqtt.Subscribe;

public class MqttV5Service implements MqttInterface {
    MqttAsyncClient client;

    @Override
    public void connect(RunContext runContext, AbstractMqttConnection connection, SSLSocketFactory sslSocketFactory) throws Exception {
        try {
            client = new MqttAsyncClient(
                runContext.render(connection.getServer()).as(String.class).orElse(null),
                runContext.render(connection.getClientId()).as(String.class).orElse(null),
                new MemoryPersistence()
            );

            var connectOptions = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();

            if (connection.getConnectionTimeout() != null) {
                connectOptions.setConnectionTimeout((int) runContext.render(connection.getConnectionTimeout()).as(Duration.class).orElseThrow().toSeconds());
            }

            if (connection.getAuthMethod() != null) {
                connectOptions.setAuthMethod(runContext.render(connection.getAuthMethod()).as(String.class).orElseThrow());
            }

            if (connection.getUsername() != null) {
                connectOptions.setUserName(runContext.render(connection.getUsername()).as(String.class).orElseThrow());
            }

            if (connection.getPassword() != null) {
                connectOptions.setPassword(runContext.render(connection.getPassword()).as(String.class).orElseThrow().getBytes(StandardCharsets.UTF_8));
            }

            if (sslSocketFactory != null) {
                connectOptions.setSocketFactory(sslSocketFactory);
            }

            if (connection.getHttpsHostnameVerificationEnabled() != null) {
                connectOptions.setHttpsHostnameVerificationEnabled(runContext.render(connection.getHttpsHostnameVerificationEnabled()).as(Boolean.class).orElseThrow());
            }

            var connect = client.connect(connectOptions);
            connect.waitForCompletion();
        } catch (MqttException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    @Override
    public void publish(RunContext runContext, Publish publish, byte[] message) throws Exception {
        MqttMessage mqttMessage = new MqttMessage();

        mqttMessage.setPayload(message);
        mqttMessage.setRetained(runContext.render(publish.getRetain()).as(Boolean.class).orElseThrow());
        mqttMessage.setQos(runContext.render(publish.getQos()).as(Integer.class).orElseThrow());

        String responseTopic = publish.getResponseTopic() == null ? null :
            runContext.render(publish.getResponseTopic()).as(String.class).orElse(null);

        String correlationData = publish.getCorrelationData() == null ? null :
            runContext.render(publish.getCorrelationData()).as(String.class).orElse(null);

        if (responseTopic != null || correlationData != null) {
            MqttProperties properties = new MqttProperties();

            if (responseTopic != null) {
                properties.setResponseTopic(responseTopic);
            }

            if (correlationData != null) {
                properties.setCorrelationData(decodeCorrelationData(correlationData));
            }

            mqttMessage.setProperties(properties);
        }

        try {
            var rTopic = runContext.render(publish.getTopic()).as(String.class)
                .orElseThrow(() -> new IllegalArgumentException("The 'topic' property is required"));
            var token = client.publish(rTopic, mqttMessage);
            token.waitForCompletion();
            checkPublishReasonCodes(token.getReasonCodes(), rTopic);
        } catch (MqttException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    private static void checkPublishReasonCodes(int[] reasonCodes, String topic) {
        if (reasonCodes == null) {
            return;
        }

        for (var reasonCode : reasonCodes) {
            if (reasonCode >= MqttReturnCode.RETURN_CODE_UNSPECIFIED_ERROR) {
                var hint = reasonCode == MqttReturnCode.RETURN_CODE_NOT_AUTHORIZED ? " Check the client's permissions on this topic." : "";
                throw new IllegalStateException(
                    "MQTT broker rejected publish to topic '%s' with reason code %d (0x%02X): %s%s"
                        .formatted(topic, reasonCode, reasonCode, new MqttException(reasonCode).getMessage(), hint)
                );
            }
        }
    }

    @Override
    public void subscribe(RunContext runContext, Subscribe subscribe, Consumer<Message> consumer) throws Exception {
        String[] topics = subscribe.topics(runContext);
        MqttSubscription[] subscriptions = new MqttSubscription[topics.length];

        //workaround for https://github.com/eclipse/paho.mqtt.java/issues/826
        final MqttProperties props = new MqttProperties();
        props.setSubscriptionIdentifiers(List.of(0));

        for (int i = 0; i < topics.length; i++) {
            subscriptions[i] = new MqttSubscription(topics[i], runContext.render(subscribe.getQos()).as(Integer.class).orElseThrow());
        }

        client.subscribe(subscriptions, null, null, (topic, message) ->
        {
            try {
                consumer.accept(
                    Message.builder()
                        .topic(topic)
                        .id(message.getId())
                        .qos(message.getQos())
                        .payload(runContext.render(subscribe.getSerdeType()).as(SerdeType.class).orElseThrow().deserialize(message.getPayload()))
                        .retain(message.isRetained())
                        .properties(message.getProperties().getValidProperties())
                        .responseTopic(message.getProperties().getResponseTopic())
                        .correlationData(encodeCorrelationData(message.getProperties().getCorrelationData()))
                        .build()
                );
            } catch (Exception e) {
                runContext.logger().error(
                    "Cannot process message {id: {}} from topic '{}'. Cause: {}",
                    message.getId(),
                    topic,
                    e.getMessage()
                );
                throw e;
            }
        }, props);
    }

    /**
     * Correlation data is binary on the wire, and a byte array is unusable in a flow expression, so
     * it is surfaced as Base64. {@link MqttV5Service#decodeCorrelationData(String)} is its inverse,
     * which is what lets a responder echo the value it received unchanged.
     */
    private static String encodeCorrelationData(byte[] correlationData) {
        return correlationData == null ? null : Base64.getEncoder().encodeToString(correlationData);
    }

    private static byte[] decodeCorrelationData(String correlationData) {
        try {
            return Base64.getDecoder().decode(correlationData);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "The 'correlationData' property must be Base64-encoded, as it is binary on the wire: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public void unsubscribe(RunContext runContext, Subscribe subscribe) throws Exception {
        IMqttToken unsubscribe = client.unsubscribe(subscribe.topics(runContext));
        unsubscribe.waitForCompletion();
    }

    @Override
    public void onDisconnected(final Consumer<Throwable> handler) {
        client.setCallback(new MqttCallback() {
            @Override
            public void disconnected(MqttDisconnectResponse disconnectResponse) {
                handler.accept(disconnectResponse.getException().getCause());
            }

            @Override
            public void mqttErrorOccurred(MqttException exception) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

            }

            @Override
            public void deliveryComplete(IMqttToken token) {

            }

            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

            }

            @Override
            public void authPacketArrived(int reasonCode, MqttProperties properties) {

            }
        });
    }

    @Override
    public void close() throws Exception {
        try {
            // A dropped connection is already disconnected, and disconnect() would throw over the original error
            if (this.client.isConnected()) {
                this.client.disconnect();
            }
            this.client.close();
        } catch (MqttException e) {
            throw new Exception(e.getMessage(), e);
        }
    }
}
