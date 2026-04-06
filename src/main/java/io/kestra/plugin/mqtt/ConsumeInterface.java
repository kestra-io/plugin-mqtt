package io.kestra.plugin.mqtt;

import java.time.Duration;

import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;
import io.kestra.core.models.annotations.PluginProperty;

public interface ConsumeInterface {
    @Schema(
        title = "The max number of rows to fetch before stopping",
        description = "It's not an hard limit and is evaluated every second"
    )
    @PluginProperty(group = "execution")
    Property<Integer> getMaxRecords();

    @Schema(
        title = "The max duration waiting for new rows",
        description = "It's not an hard limit and is evaluated every second"
    )
    @PluginProperty(group = "execution")
    Property<Duration> getMaxDuration();
}
