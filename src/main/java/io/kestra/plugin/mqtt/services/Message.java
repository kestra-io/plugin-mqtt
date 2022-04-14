package io.kestra.plugin.mqtt.services;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Message {
    Integer id;
    String topic;
    Integer qos;
    List<Byte> properties;
    Object payload;
    Boolean retain;
}
