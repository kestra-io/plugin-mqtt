package io.kestra.plugin.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.mqtt.services.SerdeType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true, startScheduler = true)
class RealtimeTriggerTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;
    @Inject
    private RunContextFactory runContextFactory;

    @SuppressWarnings("unchecked")
    @Test
    void flow() throws Exception {
        CountDownLatch queueCount = new CountDownLatch(1);
        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            queueCount.countDown();
            assertThat(execution.getLeft().getFlowId(), is("realtime"));
        });

        String messageText = "hello trigger";
        String triggerText = "Trigger is completed";
        String responseTopic = "test/realtime/reply";
        String correlationData = Base64.getEncoder().encodeToString("realtime-correlation".getBytes(StandardCharsets.UTF_8));

        Publish task = Publish.builder()
            .id(RealtimeTriggerTest.class.getSimpleName())
            .type(Publish.class.getName())
            .server(Property.ofValue("tcp://localhost:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue("test/realtime/trigger"))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .responseTopic(Property.ofValue(responseTopic))
            .correlationData(Property.ofValue(correlationData))
            .from(
                Map.of(
                    "message", messageText,
                    "notification", triggerText
                )
            )
            .build();

        repositoryLoader.load(
            Objects.requireNonNull(
                RealtimeTriggerTest.class.getClassLoader()
                    .getResource("flows/realtime.yaml")
            )
        );

        task.run(TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of()));

        boolean await = queueCount.await(1, TimeUnit.MINUTES);
        assertThat(await, is(true));

        Map<String, Object> variables = receive.blockLast()
            .getTrigger()
            .getVariables();

        Map<String, String> payload = (Map<String, String>) variables.get("payload");

        assertThat(payload.size(), is(2));

        assertThat(payload.get("message"), is(messageText));
        assertThat(payload.get("notification"), is(triggerText));

        // the trigger's Output is a hand-written mirror of Message, so these two assert the copy
        assertThat(variables.get("responseTopic"), is(responseTopic));
        assertThat(variables.get("correlationData"), is(correlationData));
    }

    @Test
    void v5ShouldFailWhenTheBrokerSendsDisconnect() throws Exception {
        var topic = "test/realtime/" + IdUtils.create();
        var clientId = IdUtils.create();

        // Retained, so the trigger's subscription receives it as soon as it is established.
        var publish = Publish.builder()
            .id(IdUtils.create())
            .type(Publish.class.getName())
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue(topic))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .from(Map.of("message", "hello disconnect"))
            .build();
        publish.run(runContextFactory.of(Map.of()));

        var trigger = RealtimeTrigger.builder()
            .id(IdUtils.create())
            .type(RealtimeTrigger.class.getName())
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(clientId))
            .topic(topic)
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .build();
        var mock = TestsUtils.mockTrigger(runContextFactory, trigger);

        // The trigger blocks the subscribing thread until it stops, so it is evaluated in the background.
        var received = new CountDownLatch(1);
        var terminated = Flux.from(trigger.evaluate(mock.getKey(), mock.getValue()))
            .doOnNext(execution -> received.countDown())
            .subscribeOn(Schedulers.boundedElastic())
            .then()
            .toFuture();

        var takeover = new MqttClient("tcp://127.0.0.1:1883", clientId, new MemoryPersistence());
        try {
            // Once a message is received, the trigger's disconnection handler is registered.
            assertThat(received.await(30, TimeUnit.SECONDS), is(true));

            // Connecting with the same client id takes the session over: the broker sends a DISCONNECT to the trigger.
            takeover.connect();

            var exception = assertThrows(ExecutionException.class, () -> terminated.get(30, TimeUnit.SECONDS));
            assertThat(exception.getCause().getMessage(), containsString("reason code " + MqttReturnCode.RETURN_CODE_SESSION_TAKEN_OVER));
            assertThat(exception.getCause().getMessage(), containsString("give this trigger a unique clientId"));
        } finally {
            trigger.stop();
            if (takeover.isConnected()) {
                takeover.disconnect();
            }
            takeover.close();
        }
    }
}
