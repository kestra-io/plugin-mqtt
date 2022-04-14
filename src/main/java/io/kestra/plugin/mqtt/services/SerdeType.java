package io.kestra.plugin.mqtt.services;

import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.nio.charset.Charset;

@io.swagger.v3.oas.annotations.media.Schema(
    title = "Serializer / Deserializer used for the payload"
)
public enum SerdeType {
    STRING,
    JSON,
    BYTES;

    Object deserialize(byte[] payload) throws IOException {
        if (this == SerdeType.JSON) {
            return JacksonMapper.ofJson(false).readValue(payload, Object.class);
        } else if (this == SerdeType.STRING) {
            return new String(payload, Charset.defaultCharset());
        } else {
            return payload;
        }
    }
}
