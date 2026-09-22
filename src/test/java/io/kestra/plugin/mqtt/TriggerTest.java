package io.kestra.plugin.mqtt;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KilledException;
import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.mqtt.services.SerdeType;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@KestraTest
class TriggerTest {

    @Inject
    private RunContextFactory runContextFactory;

    @BeforeEach
    void publishRetainedMessage() throws Exception {
        var task = Publish.builder()
            .id(TriggerTest.class.getSimpleName())
            .type(Publish.class.getName())
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue("test/trigger"))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .from(Map.of("message", "hello trigger"))
            .build();

        task.run(runContextFactory.of(Map.of()));
    }

    @Test
    @EvaluateTrigger(flow = "flows/trigger.yaml", triggerId = "watch")
    void run(Optional<Execution> optionalExecution) {
        assertThat(optionalExecution.isPresent(), is(true));
        var execution = optionalExecution.get();
        var messagesCount = (Integer) execution.getTrigger().getVariables().get("messagesCount");
        assertThat(messagesCount, greaterThanOrEqualTo(1));
    }

    @Test
    void killShouldUnblockTheTriggerEvaluation() {
        // Neither maxRecords nor maxDuration: without the kill the polling evaluation never ends.
        var trigger = unboundedTrigger("test/" + IdUtils.create());
        var mock = TestsUtils.mockTrigger(runContextFactory, trigger);

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            CompletableFuture.runAsync(trigger::kill, CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS));

            assertThrows(KilledException.class, () -> trigger.evaluate(mock.getKey(), mock.getValue()));
        });
    }

    @Test
    void stopShouldEndTheTriggerEvaluationKeepingMessages() throws Exception {
        var topic = "test/" + IdUtils.create();

        // Retained, so the trigger's subscription receives it as soon as it is established.
        var publish = Publish.builder()
            .id(TriggerTest.class.getSimpleName())
            .type(Publish.class.getName())
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue(topic))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .from(Map.of("message", "hello trigger stop"))
            .build();
        publish.run(runContextFactory.of(Map.of()));

        // Neither maxRecords nor maxDuration: without the stop the polling evaluation never ends.
        var trigger = unboundedTrigger(topic);
        var mock = TestsUtils.mockTrigger(runContextFactory, trigger);

        Optional<Execution> optionalExecution = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            CompletableFuture.runAsync(trigger::stop, CompletableFuture.delayedExecutor(3, TimeUnit.SECONDS));

            return trigger.evaluate(mock.getKey(), mock.getValue());
        });

        assertThat(optionalExecution.isPresent(), is(true));
        var messagesCount = (Integer) optionalExecution.get().getTrigger().getVariables().get("messagesCount");
        assertThat(messagesCount, greaterThanOrEqualTo(1));
    }

    private Trigger unboundedTrigger(String topic) {
        return Trigger.builder()
            .id(IdUtils.create())
            .type(Trigger.class.getName())
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(topic)
            .serdeType(Property.ofValue(SerdeType.JSON))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .build();
    }
}
