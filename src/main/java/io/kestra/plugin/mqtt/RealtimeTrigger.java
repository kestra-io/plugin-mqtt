package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.services.Message;
import io.kestra.plugin.mqtt.services.MqttFactory;
import io.kestra.plugin.mqtt.services.MqttInterface;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger flow per MQTT message",
    description = "Subscribes to MQTT topics and starts one execution immediately for each incoming message. Defaults to JSON payloads with QoS 1 and keeps consuming until stopped or disconnected; use `Trigger` for scheduled batch pulls instead."
)
@Plugin(
    examples = {
        @Example(
            title = "Consume a message from MQTT topics in real-time.",
            full = true,
            code = """
                id: mqtt_realtime_trigger
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ trigger.payload }}"

                triggers:
                  - id: realtime_trigger
                    type: io.kestra.plugin.mqtt.RealtimeTrigger
                    server: tcp://localhost:1883
                    clientId: kestraProducer
                    qos: 1
                    topic:
                      - kestra/sensors/cpu
                      - kestra/sensors/mem
                    serdeType: JSON"""
        )
    }
)
public class RealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface, TriggerOutput<RealtimeTrigger.Output>, SubscribeInterface, MqttPropertiesInterface {

    @Builder.Default
    @NotNull
    private Property<AbstractMqttConnection.Version> mqttVersion = Property.ofValue(AbstractMqttConnection.Version.V5);

    private Property<String> server;

    private Property<String> clientId;

    private Property<Duration> connectionTimeout;

    private Property<Boolean> httpsHostnameVerificationEnabled;

    private Property<String> authMethod;

    private Property<String> username;

    private Property<String> password;

    private Object topic;

    private Property<String> crt;

    @Builder.Default
    private Property<SerdeType> serdeType = Property.ofValue(SerdeType.JSON);

    @Builder.Default
    private Property<Integer> qos = Property.ofValue(1);

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean isActive = new AtomicBoolean(true);

    @Builder.Default
    @Getter(AccessLevel.NONE)
    private final CountDownLatch waitForTermination = new CountDownLatch(1);

    @Override
    public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
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
            .mqttVersion(this.mqttVersion)
            .topic(this.topic)
            .serdeType(this.serdeType)
            .qos(this.qos)
            .build();


        return Flux
            .from(publisher(task, conditionContext.getRunContext()))
            .map(record -> TriggerService.generateRealtimeExecution(this, conditionContext, context, new Output(record)));
    }

    public Publisher<Message> publisher(final Subscribe task, final RunContext runContext) throws Exception {
        final MqttInterface connection = MqttFactory.create(runContext, task);

        return Flux.create(emitter -> {
            try {

                final AtomicReference<Throwable> error = new AtomicReference<>();

                // The MQTT client is automatically shutdown if an exception is thrown in the client
                // e.g., while processing a message
                connection.onDisconnected(throwable -> {
                    error.set(throwable);
                    isActive.set(false); // proactively stop consuming
                });

                emitter.onDispose(() -> {
                    try {
                        connection.unsubscribe(runContext, task);
                        connection.close();
                    } catch (Exception e) {
                        runContext.logger().debug("Error while closing connection: " + e.getMessage());
                    } finally {
                        this.waitForTermination.countDown();
                    }
                });

                connection.subscribe(runContext, task, emitter::next);

                busyWait();

                // dispose
                if (error.get() != null) {
                    emitter.error(error.get());
                } else {
                    emitter.complete();
                }
            } catch (Exception e) {
                isActive.set(false);
                emitter.error(e);
            }
        });
    }

    private void busyWait() {
        while (isActive.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isActive.set(false); // proactively stop consuming
            }
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void kill() {
        stop(true);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void stop() {
        stop(false); // must be non-blocking
    }

    private void stop(boolean wait) {
        if (!isActive.compareAndSet(true, false)) {
            return;
        }
        if (wait) {
            try {
                this.waitForTermination.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Getter
    @AllArgsConstructor
    public class Output implements io.kestra.core.models.tasks.Output {

        private Integer id;

        private String topic;

        private Integer qos;

        private List<Byte> properties;

        private Object payload;

        private Boolean retain;

        public Output(Message message) {
            this.id = message.getId();
            this.topic = message.getTopic();
            this.qos = message.getQos();
            this.properties = message.getProperties();
            this.payload = message.getPayload();
            this.retain = message.getRetain();
        }
    }
}
