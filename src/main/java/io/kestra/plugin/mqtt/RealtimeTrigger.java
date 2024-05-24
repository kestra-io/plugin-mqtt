package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Consume a message in real-time from MQTT topics and create one execution per message."
)
@Plugin(
    examples = {
        @Example(
            code = {
                "server: tcp://localhost:1883",
                "clientId: kestraProducer",
                "topic: ",
                " - kestra/sensors/cpu",
                " - kestra/sensors/mem",
                "serdeType: JSON",
                "maxRecords: 10",
            }
        )
    },
    beta = true
)
public class RealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface, TriggerOutput<RealtimeTrigger.Output>, SubscribeInterface, MqttPropertiesInterface {

    @Builder.Default
    @NotNull
    private AbstractMqttConnection.Version version = AbstractMqttConnection.Version.V5;

    private String server;

    private String clientId;

    private Duration connectionTimeout;

    private Boolean httpsHostnameVerificationEnabled;

    private String authMethod;

    private String username;

    private String password;

    private Object topic;

    @Builder.Default
    private SerdeType serdeType = SerdeType.JSON;

    @Builder.Default
    private Integer qos = 1;

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
            .version(this.version)
            .server(this.server)
            .clientId(this.clientId)
            .connectionTimeout(this.connectionTimeout)
            .httpsHostnameVerificationEnabled(this.httpsHostnameVerificationEnabled)
            .authMethod(this.authMethod)
            .username(this.username)
            .password(this.password)
            .version(this.version)
            .topic(this.topic)
            .serdeType(this.serdeType)
            .qos(this.qos)
            .build();


        return Flux
            .from(publisher(task, conditionContext.getRunContext()))
            .map(record -> TriggerService.generateRealtimeExecution(this, context, new Output(record)));
    }

    public Publisher<Message> publisher(final Subscribe task, final RunContext runContext) throws Exception {
        MqttInterface connection = MqttFactory.create(runContext, task);

        return Flux.create(emitter -> {
            try {
                emitter.onDispose(() -> {
                    try {
                        connection.unsubscribe(runContext, task);
                        connection.close();
                    } catch (Exception e) {
                        runContext.logger().warn("Error while closing connection: " + e.getMessage());
                    } finally {
                        this.waitForTermination.countDown();
                    }
                });

                connection.subscribe(runContext, task, emitter::next);

                busyWait();
                emitter.complete();

            } catch (Exception e) {
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
