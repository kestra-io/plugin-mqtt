package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public interface SubscribeInterface {
    @Schema(
        title = "Topics to consume",
        description = "String or list of strings; when multiple topics are provided they are all subscribed."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    Object getTopic();

}
