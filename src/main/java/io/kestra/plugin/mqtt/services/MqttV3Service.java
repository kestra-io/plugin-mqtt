package io.kestra.plugin.mqtt.services;

import com.google.common.primitives.Ints;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.Publish;
import io.kestra.plugin.mqtt.Subscribe;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

public class MqttV3Service implements MqttInterface {
    MqttAsyncClient client;

    @Getter
    @Setter
    private String ca;

    @Override
    public void connect(RunContext runContext, AbstractMqttConnection connection) throws Exception {
        try {
            client = new MqttAsyncClient(
                runContext.render(connection.getServer()),
                runContext.render(connection.getClientId()),
                new MemoryPersistence()
            );

            org.eclipse.paho.client.mqttv3.MqttConnectOptions connectOptions = new org.eclipse.paho.client.mqttv3.MqttConnectOptions();

            if (connection.getConnectionTimeout() != null) {
                connectOptions.setConnectionTimeout((int) connection.getConnectionTimeout().toSeconds());
            }

            if (connection.getUsername() != null) {
                connectOptions.setUserName(runContext.render(connection.getUsername()));
            }

            if (connection.getPassword() != null) {
                connectOptions.setPassword(runContext.render(connection.getPassword()).toCharArray());
            }

            if (!StringUtils.isBlank(ca) && Path.of(ca).toFile().exists()) {
                SSLSocketFactory socketFactory = CustomSSLSocketFactory.createSSLSocketFactory(ca);
                connectOptions.setCleanSession(true);
                connectOptions.setConnectionTimeout(60);
                connectOptions.setKeepAliveInterval(60);
                connectOptions.setSocketFactory(socketFactory);
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

    @SuppressWarnings("DuplicatedCode")
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

        IMqttMessageListener messageListener = (topic, message) -> {
            consumer.accept(Message.builder()
                .topic(topic)
                .id(message.getId())
                .qos(message.getQos())
                .payload(subscribe.getSerdeType().deserialize(message.getPayload()))
                .retain(message.isRetained())
                .build());
        };

        IMqttMessageListener[] listeners = new IMqttMessageListener[topics.length];
        Arrays.fill(listeners, messageListener);


        ArrayList<Integer> qos = new ArrayList<>();
        for (int i = 0; i < topics.length; i++) {
            qos.add(subscribe.getQos());
        }

        client.subscribe(topics, Ints.toArray(qos), listeners);
    }

    @Override
    public void unsubscribe(RunContext runContext, Subscribe subscribe) throws Exception {
        IMqttToken unsubscribe = client.unsubscribe(subscribe.topics(runContext));
        unsubscribe.waitForCompletion();
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
