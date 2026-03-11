package io.kestra.plugin.mqtt.services;

import java.util.List;

import lombok.Builder;
import lombok.Value;

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
