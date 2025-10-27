package io.kestra.plugin.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.property.Data;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.mqtt.services.MqttFactory;
import io.kestra.plugin.mqtt.services.MqttInterface;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "Publish a message to an MQTT topic."
)
@Plugin(
    metrics = {
        @Metric(name = "records", type = Counter.TYPE),
        @Metric(name = "duration", type = Timer.TYPE)
    },
    examples = {
        @Example(
            full = true,
            code = """
                id: mqtt_publish
                namespace: company.team

                tasks:
                  - id: publish
                    type: io.kestra.plugin.mqtt.Publish
                    server: tcp://localhost:1883
                    clientId: kestraProducer
                    topic: kestra/sensors/cpu
                    serdeType: JSON
                    retain: true
                    from:
                      type: "sensors"
                      value: 1.23
                """
        ),
        @Example(
            full = true,
            code = """
                id: mqtt_publish
                namespace: company.team

                tasks:
                  - id: publish
                    type: io.kestra.plugin.mqtt.Publish
                    server: ssl://localhost:8883
                    clientId: kestraProducer
                    topic: kestra/sensors/cpu
                    crt: /home/path/to/ca.crt
                    serdeType: JSON
                    retain: true
                    from:
                      type: "sensors"
                      value: 1.23
                """
        )
    }
)
public class Publish extends AbstractMqttConnection
        implements RunnableTask<Publish.Output>, MqttPropertiesInterface, Data.From {

    @Schema(
        title = "Topic where to send message"
    )
    @NotNull
    private Property<String> topic;

    private Object from;

    @Schema(
        title = "Whether or not the publish message should be retained by the messaging engine.",
        description = "Sending a message with retained set to true and with an empty byte array as the payload (e.g., `null`) "
            + "will clear the retained message from the server."
    )
    @NotNull
    @Builder.Default
    private Property<Boolean> retain = Property.ofValue(false);

    private Property<SerdeType> serdeType;

    @Builder.Default
    private Property<Integer> qos = Property.ofValue(1);

    @Override
    public Publish.Output run(RunContext runContext) throws Exception {
        long startTime = System.nanoTime();

        MqttInterface connection = MqttFactory.create(runContext, this);

        String topic = runContext.render(this.topic).as(String.class).orElseThrow();

        Integer count = Data.from(from).read(runContext)
                .map(throwFunction(row -> {
                    connection.publish(runContext, this, this.serialize(row, runContext));
                    return 1;
                }))
                .reduce(Integer::sum)
                .blockOptional().orElse(0);

        runContext.metric(Counter.of("records", count, "topic", topic));
        runContext.metric(Timer.of("duration", Duration.ofNanos(System.nanoTime() - startTime)));

        connection.close();

        return Output.builder()
                .messagesCount(count)
                .build();
    }

    private byte[] serialize(Object row, RunContext runContext)
            throws JsonProcessingException, IllegalVariableEvaluationException {
        if (runContext.render(this.serdeType).as(SerdeType.class).orElseThrow() == SerdeType.JSON) {
            return JacksonMapper.ofJson().writeValueAsBytes(row);
        } else if (runContext.render(this.serdeType).as(SerdeType.class).orElseThrow() == SerdeType.STRING) {
            return ((String) row).getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Unexpetected serdeType '" + this.serdeType + "'");
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Number of message published"
        )
        private final Integer messagesCount;
    }
}