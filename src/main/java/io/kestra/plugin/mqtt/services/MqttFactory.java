package io.kestra.plugin.mqtt.services;

import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;

public abstract class MqttFactory {
    public static MqttInterface create(RunContext runContext, AbstractMqttConnection connection) throws Exception {
        if (runContext.render(connection.getMqttVersion()).as(AbstractMqttConnection.Version.class).orElseThrow() == AbstractMqttConnection.Version.V5) {
            MqttV5Service mqttV5Service = new MqttV5Service();
            mqttV5Service.setCrt(runContext.render(connection.getCrt()).as(String.class).orElse(null));
            mqttV5Service.connect(runContext, connection);

            return mqttV5Service;
        } else {
            MqttV3Service mqttV3Service = new MqttV3Service();
            mqttV3Service.setCrt(runContext.render(connection.getCrt()).as(String.class).orElse(null));
            mqttV3Service.connect(runContext, connection);

            return mqttV3Service;
        }
    }
}
