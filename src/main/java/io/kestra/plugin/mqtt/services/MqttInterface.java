package io.kestra.plugin.mqtt.services;

import java.util.function.Consumer;

import javax.net.ssl.SSLSocketFactory;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.Publish;
import io.kestra.plugin.mqtt.Subscribe;

public interface MqttInterface {
    void connect(RunContext runContext, AbstractMqttConnection connection, SSLSocketFactory sslSocketFactory) throws Exception;

    void publish(RunContext runContext, Publish publish, byte[] message) throws Exception;

    void subscribe(RunContext runContext, Subscribe subscribe, Consumer<Message> consumer) throws Exception;

    void unsubscribe(RunContext runContext, Subscribe subscribe) throws Exception;

    void close() throws Exception;

    void onDisconnected(final Consumer<Throwable> handler);
}
