package io.kestra.plugin.mqtt.services;

import java.util.Map;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Message {
    Integer id;
    String topic;
    Integer qos;
    Map<String, Object> properties;
    Object payload;
    Boolean retain;
    String responseTopic;
    String correlationData;
}
