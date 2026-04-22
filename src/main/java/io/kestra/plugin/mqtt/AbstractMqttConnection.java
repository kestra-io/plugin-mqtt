package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.PluginProperty;
import java.time.Duration;

import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractMqttConnection extends Task implements MqttConnectionInterface {
    @Builder.Default
    @NotNull
    private Property<Version> mqttVersion = Property.ofValue(Version.V5);

    private Property<String> server;

    private Property<String> clientId;

    private Property<Duration> connectionTimeout;

    private Property<Boolean> httpsHostnameVerificationEnabled;

    private Property<String> authMethod;

    @PluginProperty(secret = true)
    private Property<String> username;

    @PluginProperty(secret = true)
    private Property<String> password;

    private Property<String> crt;

    public enum Version {
        V3,
        V5
    }
}
