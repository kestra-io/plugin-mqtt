package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import javax.validation.constraints.NotNull;

public interface SubscribeInterface {
    @Schema(
        title = "Topic where to consume message",
        description = "Can be a string or a List of string to consume from multiple topic"
    )
    @NotNull
    @PluginProperty(dynamic = true)
    Object getTopic();

    @Schema(
        title = "The max number of rows to fetch before stopping",
        description = "It's not an hard limit and is evaluated every second"
    )
    @PluginProperty(dynamic = false)
    Integer getMaxRecords();

    @Schema(
        title = "The max duration waiting for new rows",
        description = "It's not an hard limit and is evaluated every second"
    )
    @PluginProperty(dynamic = false)
    Duration getMaxDuration();
}
