package io.kestra.plugin.mqtt.services;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import jakarta.annotation.Nullable;

public abstract class MqttFactory {
    public static MqttInterface create(RunContext runContext, AbstractMqttConnection connection, @Nullable String caUri) throws Exception {
        if (connection.getVersion() == AbstractMqttConnection.Version.V5) {
            MqttV5Service mqttV5Service = new MqttV5Service();
            mqttV5Service.setCrt(caUri);
            mqttV5Service.connect(runContext, connection);

            return mqttV5Service;
        } else {
            MqttV3Service mqttV3Service = new MqttV3Service();
            mqttV3Service.setCrt(caUri);
            mqttV3Service.connect(runContext, connection);

            return mqttV3Service;
        }
    }
}
