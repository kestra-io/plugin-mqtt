package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

public interface MqttPropertiesInterface {
    @Schema(
        title = "Serializer / Deserializer used for the payload"
    )
    @NotNull
    @PluginProperty(dynamic = false)
    SerdeType getSerdeType();

    @Schema(
        title = "Sets the quality of service for this message.",
        description = "* **Quality of Service 0**: indicates that a message should be delivered at most once " +
            "(zero or one times). The message will not be persisted to disk, and will not be acknowledged across " +
            "the network. This QoS is the fastest, but should only be used for messages which are not valuable - " +
            "note that if the server cannot process the message (for example, there is an authorization problem). " +
            "Also known as \"fire and forget\".\n" +
            "* **Quality of Service 1**: indicates that a message should be delivered at least once (one or more times)." +
            " The message can only be delivered safely if it can be persisted, so the application must supply a means " +
            "of persistence using MqttConnectOptions. If a persistence mechanism is not specified, the message will" +
            " not be delivered in the event of a client failure. The message will be acknowledged across the network.\n" +
            "* **Quality of Service 2**: indicates that a message should be delivered once. The message will be " +
            "persisted to disk, and will be subject to a two-phase acknowledgement across the network. The message " +
            "can only be delivered safely if it can be persisted, so the application must supply a means of persistence" +
            " using MqttConnectOptions. If a persistence mechanism is not specified, the message will not be delivered " +
            "in the event of a client failure.\n" +
            "If persistence is not configured, QoS 1 and 2 messages will still be delivered in the event of a network " +
            "or server problem as the client will hold state in memory. If the MQTT client is shutdown or fails and " +
            "persistence is not configured then delivery of QoS 1 and 2 messages can not be maintained as " +
            "client-side state will be lost."
    )
    @NotNull
    @PluginProperty(dynamic = false)
    Integer getQos();
}
