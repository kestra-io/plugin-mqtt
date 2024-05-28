package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import jakarta.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractMqttConnection extends Task implements MqttConnectionInterface {
    @Builder.Default
    @NotNull
    private Version version = Version.V5;

    private String server;

    private String clientId;

    private Duration connectionTimeout;

    private Boolean httpsHostnameVerificationEnabled;

    private String authMethod;

    private String username;

    private String password;

    private String crt;

    public enum Version {
        V3,
        V5
    }
}
