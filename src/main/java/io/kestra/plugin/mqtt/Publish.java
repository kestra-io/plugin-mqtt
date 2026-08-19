package io.kestra.plugin.mqtt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;

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

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@NoArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
@Schema(
    title = "Publish messages to MQTT topics",
    description = "Publishes data from `from` to a single MQTT topic using the chosen serializer (e.g., JSON or STRING) and QoS (default 1). `retain` defaults to false; send an empty payload with `retain: true` to clear retained state on the broker."
)
@Plugin(
    metrics = {
        @Metric(name = "records", type = Counter.TYPE),
        @Metric(name = "duration", type = Timer.TYPE)
    },
    examples = {
        @Example(
            title = "Publish a JSON message over MQTT (TCP)",
            full = true,
            code = """
                id: mqtt_publish
                namespace: company.team

                tasks:
                  - id: publish
                    type: io.kestra.plugin.mqtt.Publish
                    server: tcp://localhost:1883
                    clientId: kestraProducer
                    qos: 1
                    topic: kestra/sensors/cpu
                    serdeType: JSON
                    retain: true
                    from:
                      type: "sensors"
                      value: 1.23
                """
        ),
        @Example(
            title = "Publish a JSON message over secure MQTT (TLS)",
            full = true,
            code = """
                id: mqtt_publish_ssl
                namespace: company.team

                tasks:
                  - id: publish
                    type: io.kestra.plugin.mqtt.Publish
                    server: ssl://localhost:8883
                    clientId: kestraProducer
                    qos: 2
                    topic: kestra/sensors/cpu
                    crt: "{{ secret('MQTT_CA_CERT') }}"
                    serdeType: JSON
                    retain: true
                    from:
                      type: "sensors"
                      value: 1.23
                """
        ),
        @Example(
            title = "Send an MQTT 5 request, asking for the reply on a topic of its own",
            full = true,
            code = """
                id: mqtt_request
                namespace: company.team

                tasks:
                  - id: request
                    type: io.kestra.plugin.mqtt.Publish
                    server: tcp://localhost:1883
                    clientId: kestraRequester
                    topic: kestra/requests
                    responseTopic: kestra/replies/{{ execution.id }}
                    correlationData: "{{ execution.id | base64encode }}"
                    serdeType: JSON
                    from:
                      question: "what is the cpu load?"
                """
        ),
        @Example(
            title = "Reply to an MQTT 5 request, echoing its correlation data",
            full = true,
            code = """
                id: mqtt_reply
                namespace: company.team

                triggers:
                  - id: request
                    type: io.kestra.plugin.mqtt.RealtimeTrigger
                    server: tcp://localhost:1883
                    topic: kestra/requests
                    serdeType: JSON

                tasks:
                  - id: reply
                    type: io.kestra.plugin.mqtt.Publish
                    server: tcp://localhost:1883
                    clientId: kestraResponder
                    topic: "{{ trigger.responseTopic }}"
                    correlationData: "{{ trigger.correlationData }}"
                    serdeType: JSON
                    from:
                      answer: "{{ trigger.payload.question }}"
                """
        )
    }
)
public class Publish extends AbstractMqttConnection
    implements RunnableTask<Publish.Output>, MqttPropertiesInterface, Data.From {

    @Schema(
        title = "Topic to publish to"
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> topic;

    @Schema(
        title = io.kestra.core.models.property.Data.From.TITLE,
        description = io.kestra.core.models.property.Data.From.DESCRIPTION
    )
    @NotNull
    @PluginProperty(group = "main")
    private Object from;

    @Schema(
        title = "Whether or not the publish message should be retained by the messaging engine",
        description = "Sending a message with retained set to true and with an empty byte array as the payload (e.g., `null`) "
            + "will clear the retained message from the server."
    )
    @NotNull
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Boolean> retain = Property.ofValue(false);

    @Schema(
        title = "MQTT 5 response topic",
        description = "The topic a responder is expected to publish its reply to, sent as the MQTT 5 "
            + "`Response Topic` message property. Ignored for MQTT 3.1.1, which has no message properties."
    )
    @PluginProperty(group = "advanced")
    private Property<String> responseTopic;

    @Schema(
        title = "MQTT 5 correlation data, Base64-encoded",
        description = "Opaque data a responder echoes back so a reply can be matched to its request, sent as "
            + "the MQTT 5 `Correlation Data` message property. Correlation data is binary on the wire, so this "
            + "property is **Base64-encoded** — use `{{ 'my-id' | base64encode }}` for a text value — and "
            + "`Subscribe`, `Trigger` and `RealtimeTrigger` surface it in the same encoding, so a received value "
            + "can be echoed back unchanged. Ignored for MQTT 3.1.1, which has no message properties."
    )
    @PluginProperty(group = "advanced")
    private Property<String> correlationData;

    @PluginProperty(group = "main")
    private Property<SerdeType> serdeType;

    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> qos = Property.ofValue(1);

    @Override
    public Publish.Output run(RunContext runContext) throws Exception {
        long startTime = System.nanoTime();

        MqttInterface connection = MqttFactory.create(runContext, this);

        String rTopic = runContext.render(this.topic).as(String.class).orElseThrow();

        int count;
        SerdeType rSerdeType = runContext.render(this.serdeType).as(SerdeType.class).orElseThrow();

        if (rSerdeType == SerdeType.STRING) {
            Iterable<?> rows = (from instanceof Iterable<?> iterable) ? iterable : List.of(from);

            rows.forEach(throwConsumer(row ->
            {
                String value = runContext.render(row.toString());
                connection.publish(
                    runContext,
                    this,
                    value.getBytes(StandardCharsets.UTF_8)
                );
            }));

            count = (rows instanceof Collection<?> c) ? c.size() : 1;
        } else {
            count = Data.from(from).read(runContext)
                .map(throwFunction(row ->
                {
                    connection.publish(runContext, this, this.serialize(row, runContext));
                    return 1;
                }))
                .reduce(Integer::sum)
                .blockOptional().orElse(0);

        }

        runContext.metric(Counter.of("records", count, "topic", rTopic));
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
            title = "Number of messages published"
        )
        private final Integer messagesCount;
    }
}
