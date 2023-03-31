package io.kestra.plugin.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.mqtt.services.MqttFactory;
import io.kestra.plugin.mqtt.services.MqttInterface;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.kestra.core.utils.Rethrow.throwFunction;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Produce message in a MQTT topic"
)
@Plugin(
    examples = {
        @Example(
            code = {
                "server: tcp://localhost:1883",
                "clientId: kestraProducer",
                "topic: kestra/sensors/cpu",
                "serdeType: JSON",
                "retain: true",
                "from: ",
                "  type: \"sensors\"",
                "  value: 1.23",
            }
        )
    }
)
public class Publish extends AbstractMqttConnection implements RunnableTask<Publish.Output>, MqttPropertiesInterface {
    @Schema(
        title = "Topic where to send message"
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private String topic;

    @Schema(
        title = "Source of message send",
        description = "Can be an internal storage uri, a map or a list.",
        anyOf = {String.class, Object[].class, Map.class }
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Object from;

    @Schema(
        title = "Whether or not the publish message should be retained by the messaging engine. ",
        description = "Sending a message with retained set to true and with an empty byte array as the " +
            "payload e.g. `null` will clear the retained message from the server."
    )
    @NotNull
    @PluginProperty(dynamic = false)
    @Builder.Default
    private Boolean retain = false;

    private SerdeType serdeType;

    @Builder.Default
    private Integer qos = 1;

    @SuppressWarnings("unchecked")
    @Override
    public Publish.Output run(RunContext runContext) throws Exception {
        MqttInterface connection = MqttFactory.create(runContext, this);

        Integer count = 1;

        String topic = runContext.render(this.topic);

        if (this.from instanceof String || this.from instanceof List) {
            Flowable<Object> flowable;
            if (this.from instanceof String) {
                URI from = new URI(runContext.render((String) this.from));
                try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(runContext.uriToInputStream(from)))) {
                    flowable = Flowable.create(FileSerde.reader(inputStream), BackpressureStrategy.BUFFER);
                }
            } else {
                flowable = Flowable.fromArray(((List<?>) this.from).stream()
                    .map(throwFunction(row -> {
                        if (row instanceof Map) {
                            return runContext.render((Map<String, Object>) row);
                        } else if (row instanceof String) {
                            return runContext.render((String) row);
                        } else {
                            return row;
                        }
                    })).toArray());
            }

            Flowable<Integer> resultFlowable = flowable.map(row -> {
                connection.publish(runContext, this, this.serialize(row));
                return 1;
            });

            count = resultFlowable
                .reduce(Integer::sum)
                .blockingGet();
        } else {
            connection.publish(runContext, this, this.serialize(runContext.render((Map<String, Object>) this.from)));
        }

        runContext.metric(Counter.of("records", count, "topic", topic));

        connection.close();

        return Output.builder()
            .messagesCount(count)
            .build();
    }

    private byte[] serialize(Object row) throws JsonProcessingException {
        if (this.serdeType == SerdeType.JSON) {
            return JacksonMapper.ofJson().writeValueAsBytes(row);
        } else if (this.serdeType == SerdeType.STRING) {
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
