package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow on periodic message consumption from MQTT topics.",
    description = "Note that you don't need an extra task to consume the message from the event trigger. The trigger will automatically consume messages and you can retrieve their content in your flow using the `{{ trigger.uri }}` variable. If you would like to consume each message from MQTT topics in real-time and create one execution per message, you can use the [io.kestra.plugin.mqtt.RealtimeTrigger](https://kestra.io/plugins/plugin-mqtt/triggers/io.kestra.plugin.mqtt.realtimetrigger) instead."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: mqtt_trigger
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ trigger.payload }}"

                triggers:
                  - id: trigger
                    type: io.kestra.plugin.mqtt.Trigger
                    server: tcp://localhost:1883
                    clientId: kestraProducer
                    topic:
                      - kestra/sensors/cpu
                      - kestra/sensors/mem
                    serdeType: JSON
                    maxRecords: 10
            """
        )
    }
)
public class Trigger extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<Subscribe.Output>, SubscribeInterface, ConsumeInterface, MqttPropertiesInterface {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

    @Builder.Default
    @NotNull
    private Property<AbstractMqttConnection.Version> mqttVersion = Property.of(AbstractMqttConnection.Version.V5);

    private Property<String> server;

    private Property<String> clientId;

    private Property<Duration> connectionTimeout;

    private Property<Boolean> httpsHostnameVerificationEnabled;

    private Property<String> authMethod;

    private Property<String> username;

    private Property<String> password;

    private Property<String> crt;

    private Object topic;

    @Builder.Default
    private Property<SerdeType> serdeType = Property.of(SerdeType.JSON);

    @Builder.Default
    private Property<Integer> qos = Property.of(1);

    private Property<Integer> maxRecords;

    private Property<Duration> maxDuration;

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        Subscribe task = Subscribe.builder()
            .id(this.id)
            .type(Subscribe.class.getName())
            .mqttVersion(this.mqttVersion)
            .server(this.server)
            .clientId(this.clientId)
            .connectionTimeout(this.connectionTimeout)
            .httpsHostnameVerificationEnabled(this.httpsHostnameVerificationEnabled)
            .authMethod(this.authMethod)
            .username(this.username)
            .password(this.password)
            .crt(this.crt)
            .mqttVersion(this.mqttVersion)
            .topic(this.topic)
            .serdeType(this.serdeType)
            .qos(this.qos)
            .maxRecords(this.maxRecords)
            .maxDuration(this.maxDuration)
            .build();
        Subscribe.Output run = task.run(runContext);

        if (logger.isDebugEnabled()) {
            logger.debug("Found '{}' messages from '{}'", run.getMessagesCount(), task.topics(runContext));
        }

        if (run.getMessagesCount() == 0) {
            return Optional.empty();
        }

        Execution execution = TriggerService.generateExecution(this, conditionContext, context, run);

        return Optional.of(execution);
    }
}
