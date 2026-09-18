package io.kestra.plugin.mqtt;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.KilledException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Metric;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
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

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwRunnable;
import io.kestra.core.models.annotations.PluginProperty;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Subscribe and buffer MQTT messages",
    description = "Subscribes to one or more MQTT topics, writes received messages to internal storage, and returns the `uri` plus `messagesCount`. Stops when `maxRecords` or `maxDuration` is reached; defaults to JSON payloads with QoS 1."
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
                id: mqtt_subscribe
                namespace: company.team

                tasks:
                  - id: subscribe
                    type: io.kestra.plugin.mqtt.Subscribe
                    server: tcp://localhost:1883
                    clientId: kestraProducer
                    qos: 1
                    maxDuration: 30s
                    topic:
                      - kestra/sensors/cpu
                      - kestra/sensors/mem
                    serdeType: JSON
                    maxRecords: 10
                """
        ),
        @Example(
            title = "Subscribe to MQTT topics over TLS",
            full = true,
            code = """
                id: mqtt_subscribe_ssl
                namespace: company.team

                tasks:
                  - id: subscribe
                    type: io.kestra.plugin.mqtt.Subscribe
                    server: ssl://localhost:8883
                    clientId: kestraProducer
                    qos: 2
                    maxDuration: 1m
                    topic:
                      - kestra/sensors/cpu
                      - kestra/sensors/mem
                    crt: "{{ secret('MQTT_CA_CERT') }}"
                    serdeType: JSON
                    maxRecords: 10
                """
        )
    }
)
public class Subscribe extends AbstractMqttConnection implements RunnableTask<Subscribe.Output>, SubscribeInterface, ConsumeInterface, MqttPropertiesInterface {
    @PluginProperty(group = "main")
    private Object topic;

    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<SerdeType> serdeType = Property.ofValue(SerdeType.JSON);

    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> qos = Property.ofValue(1);

    @PluginProperty(group = "advanced")
    private Property<Integer> maxRecords;

    @PluginProperty(group = "execution")
    private Property<Duration> maxDuration;

    // Lifecycle state, not config. Never reset in run(): attempts get a fresh instance, so a reset could only drop a just-delivered kill.
    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Builder.Default
    private final AtomicBoolean isKilled = new AtomicBoolean(false);

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Builder.Default
    private final AtomicBoolean isStopped = new AtomicBoolean(false);

    @Override
    public void kill() {
        this.isKilled.set(true);
    }

    // Keeps the messages already consumed: they are acknowledged on the broker, so a restarted task cannot read them again.
    @Override
    public void stop() {
        this.isStopped.set(true);
    }

    @Override
    public Output run(RunContext runContext) throws Exception {
        long startTime = System.nanoTime();

        File tempFile = runContext.workingDir().createTempFile(".ion").toFile();

        // Connect last, so nothing between the connection and the try block can leak it.
        MqttInterface connection = MqttFactory.create(runContext, this);
        Thread thread = null;

        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
            Map<String, Integer> count = new HashMap<>();
            AtomicInteger total = new AtomicInteger();
            ZonedDateTime started = ZonedDateTime.now();

            thread = Thread.ofVirtual().name("mqtt-subscribe").start(throwRunnable(() ->
            {
                connection.subscribe(runContext, this, throwConsumer(message ->
                {
                    FileSerde.write(output, message);

                    total.getAndIncrement();
                    count.compute(message.getTopic(), (s, integer) -> integer == null ? 1 : integer + 1);
                }));
            }));

            while (!this.isKilled.get() && !this.isStopped.get() && !this.ended(total, started, runContext)) {
                //noinspection BusyWait
                Thread.sleep(100);
            }

            if (this.isKilled.get()) {
                // The worker only maps a task run to KILLED when run() fails: returning here would report the kill as a success.
                throw new KilledException("MQTT subscription was killed");
            }

            connection.unsubscribe(runContext, this);
            thread.join();

            output.flush();

            count
                .forEach((s, integer) -> runContext.metric(Counter.of("records", integer, "topic", s)));

            runContext.metric(Timer.of("duration", Duration.ofNanos(System.nanoTime() - startTime)));

            return Output.builder()
                .messagesCount(count.values().stream().mapToInt(Integer::intValue).sum())
                .uri(runContext.storage().putFile(tempFile))
                .build();
        } finally {
            if (thread != null) {
                thread.interrupt();
            }

            closeConnection(runContext, connection);
        }
    }

    private static void closeConnection(RunContext runContext, MqttInterface connection) {
        try {
            connection.close();
        } catch (Exception e) {
            runContext.logger().warn("Failed to close the MQTT connection: {}", e.getMessage());
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
            title = "Number of messages consumed",
            description = "Lower than `maxRecords` when the subscription ended on `maxDuration` or on a graceful worker shutdown."
        )
        private final Integer messagesCount;

        @Schema(
            title = "URI of the internal storage file"
        )
        private URI uri;
    }
}
