package io.kestra.plugin.mqtt;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.mqtt.services.MqttFactory;
import io.kestra.plugin.mqtt.services.MqttInterface;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwRunnable;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Subscribe to messages in an MQTT topic."
)
@Plugin(
    examples = {
        @Example(
            full = true,
            code = """
                id: mqtt_subscribe
                namespace: company.team

                tasks:
                  - id: subscribe
                    type: io.kestra.plugin.mqtt.Subscribe
                    server: tcp://localhost:1883
                    clientId: kestraProducer
                    topic:
                      - kestra/sensors/cpu
                      - kestra/sensors/mem
                    serdeType: JSON
                    maxRecords: 10
                """
        ),
        @Example(
            full = true,
            code = """
                id: mqtt_subscribe
                namespace: company.team

                tasks:
                  - id: subscribe
                    type: io.kestra.plugin.mqtt.Subscribe
                    server: ssl://localhost:8883
                    clientId: kestraProducer
                    topic:
                      - kestra/sensors/cpu
                      - kestra/sensors/mem
                    crt: /home/path/to/ca.crt
                    serdeType: JSON
                    maxRecords: 10
                """
        )
    }
)
public class Subscribe extends AbstractMqttConnection implements RunnableTask<Subscribe.Output>, SubscribeInterface, ConsumeInterface, MqttPropertiesInterface {
    private Object topic;

    @Builder.Default
    private Property<SerdeType> serdeType = Property.of(SerdeType.JSON);

    @Builder.Default
    private Property<Integer> qos = Property.of(1);

    private Property<String> crt;

    private Property<Integer> maxRecords;

    private Property<Duration> maxDuration;

    @Override
    public Output run(RunContext runContext) throws Exception {
        MqttInterface connection = MqttFactory.create(runContext, this);

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();
        Thread thread = null;

        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            Map<String, Integer> count = new HashMap<>();
            AtomicInteger total = new AtomicInteger();
            ZonedDateTime started = ZonedDateTime.now();

            thread = Thread.ofVirtual().name("mqtt-subscribe").start(throwRunnable(() -> {
                connection.subscribe(runContext, this, throwConsumer(message -> {
                    FileSerde.write(output, message);

                    total.getAndIncrement();
                    count.compute(message.getTopic(), (s, integer) -> integer == null ? 1 : integer + 1);
                }));
            }));

            while (!this.ended(total, started, runContext)) {
                //noinspection BusyWait
                Thread.sleep(100);
            }

            connection.unsubscribe(runContext, this);
            thread.join();

            output.flush();

            count
                .forEach((s, integer) -> runContext.metric(Counter.of("records", integer, "topic", s)));

            return Output.builder()
                .messagesCount(count.values().stream().mapToInt(Integer::intValue).sum())
                .uri(runContext.storage().putFile(tempFile))
                .build();
        } finally {
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public String[] topics(RunContext runContext) throws IllegalVariableEvaluationException {
        if (this.topic instanceof String) {
            return List.of(runContext.render((String) this.topic)).toArray(String[]::new);
        } else if (this.topic instanceof List) {
            return runContext.render((List<String>) this.topic).toArray(String[]::new);
        } else {
            throw new IllegalArgumentException("Invalid topics with type '" + this.topic.getClass().getName() + "'");
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean ended(AtomicInteger count, ZonedDateTime start, RunContext runContext) throws IllegalVariableEvaluationException {
        var renderedMaxRecords = runContext.render(this.maxRecords).as(Integer.class);
        if (renderedMaxRecords.isPresent() && count.get() >= renderedMaxRecords.get()) {
            return true;
        }

        var renderedDuration = runContext.render(this.maxDuration).as(Duration.class);
        if (renderedDuration.isPresent() && ZonedDateTime.now().toEpochSecond() > start.plus(renderedDuration.get()).toEpochSecond()) {
            return true;
        }

        return false;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Number of message produced"
        )
        private final Integer messagesCount;

        @Schema(
            title = "URI of a kestra internal storage file"
        )
        private URI uri;
    }
}
