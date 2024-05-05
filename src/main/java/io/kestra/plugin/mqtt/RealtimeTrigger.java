package io.kestra.plugin.mqtt;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.*;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.mqtt.services.Message;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "React to and consume messages by MQTT topics"
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
    }
)
public class RealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface, TriggerOutput<RealtimeTrigger.Output>, SubscribeInterface, MqttPropertiesInterface {
    @Builder.Default
    private final Duration interval = Duration.ofSeconds(60);

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

    private Integer maxRecords;

    private Duration maxDuration;

    @Override
    public Publisher<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

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
            .maxRecords(this.maxRecords)
            .maxDuration(this.maxDuration)
            .build();

        return Flux.from(task.stream(conditionContext.getRunContext()))
            .map(record -> TriggerService.generateRealtimeExecution(this, context, new Output(record)))
            .next();
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
