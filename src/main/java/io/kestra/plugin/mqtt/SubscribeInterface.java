package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import jakarta.validation.constraints.NotNull;

public interface SubscribeInterface {
    @Schema(
        title = "Topic where to consume message",
        description = "Can be a string or a List of string to consume from multiple topic"
    )
    @NotNull
    @PluginProperty(dynamic = true)
    Object getTopic();

}
