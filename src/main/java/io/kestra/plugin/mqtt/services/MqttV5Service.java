package io.kestra.plugin.mqtt.services;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.Publish;
import io.kestra.plugin.mqtt.Subscribe;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class MqttV5Service implements MqttInterface {
    MqttAsyncClient client;

    @Getter
    @Setter
    private String crt;

    @Override
    public void connect(RunContext runContext, AbstractMqttConnection connection) throws Exception {
        try {
            client = new MqttAsyncClient(
                runContext.render(connection.getServer()),
                runContext.render(connection.getClientId()),
                new MemoryPersistence()
            );

            org.eclipse.paho.mqttv5.client.MqttConnectionOptions connectOptions = new org.eclipse.paho.mqttv5.client.MqttConnectionOptions();

            if (connection.getConnectionTimeout() != null) {
                connectOptions.setConnectionTimeout((int) connection.getConnectionTimeout().toSeconds());
            }

            if (connection.getAuthMethod() != null) {
                connectOptions.setAuthMethod(runContext.render(connection.getAuthMethod()));
            }

            if (connection.getUsername() != null) {
                connectOptions.setUserName(runContext.render(connection.getUsername()));
            }

            if (connection.getPassword() != null) {
                connectOptions.setPassword(runContext.render(connection.getPassword()).getBytes(StandardCharsets.UTF_8));
            }

            if (!StringUtils.isBlank(crt) && Path.of(crt).toFile().exists()) {
                connectOptions.setSocketFactory(CustomSSLSocketFactory.createSSLSocketFactory(crt));
            }

            if (connection.getHttpsHostnameVerificationEnabled() != null) {
                connectOptions.setHttpsHostnameVerificationEnabled(connection.getHttpsHostnameVerificationEnabled());
            }

            IMqttToken connect = client.connect(connectOptions);
            connect.waitForCompletion();
        } catch (MqttException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    @Override
    public void publish(RunContext runContext, Publish publish, byte[] message) throws Exception {
        MqttMessage mqttMessage = new MqttMessage();

        mqttMessage.setPayload(message);
        mqttMessage.setRetained(publish.getRetain());
        mqttMessage.setQos(publish.getQos());

        try {
            IMqttToken token = client.publish(runContext.render(publish.getTopic()), mqttMessage);
            token.waitForCompletion();
        } catch (MqttException e) {
            throw new Exception(e.getMessage(), e);
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
            subscriptions[i] = new MqttSubscription(topics[i], subscribe.getQos());
        }

        client.subscribe(subscriptions, null, null, (topic, message) -> {
            try {
                consumer.accept(Message.builder()
                    .topic(topic)
                    .id(message.getId())
                    .qos(message.getQos())
                    .payload(subscribe.getSerdeType().deserialize(message.getPayload()))
                    .retain(message.isRetained())
                    .properties(message.getProperties().getValidProperties())
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
            this.client.disconnect();
            this.client.close();
        } catch (MqttException e) {
            throw new Exception(e.getMessage(), e);
        }
    }
}
