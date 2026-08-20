package io.kestra.plugin.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
}
