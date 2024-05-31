package io.kestra.plugin.mqtt.services;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.Publish;
import io.kestra.plugin.mqtt.Subscribe;

import java.util.function.Consumer;

public interface MqttInterface {
    void connect(RunContext runContext, AbstractMqttConnection connection) throws Exception;

    void publish(RunContext runContext, Publish publish, byte[] message) throws Exception;

    void subscribe(RunContext runContext, Subscribe subscribe, Consumer<Message> consumer) throws Exception;

    void unsubscribe(RunContext runContext, Subscribe subscribe) throws Exception;

    void close() throws Exception;

    void onDisconnected(final Consumer<Throwable> handler);

    default MqttInterface create(AbstractMqttConnection.Version version) {
        if (version == AbstractMqttConnection.Version.V5) {
            return new MqttV5Service();
        } else {
            return new MqttV3Service();
        }
    }
}
