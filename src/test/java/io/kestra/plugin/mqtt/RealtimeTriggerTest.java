package io.kestra.plugin.mqtt;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.mqtt.services.SerdeType;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest(startRunner = true, startScheduler = true)
class RealtimeTriggerTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @SuppressWarnings("unchecked")
    @Test
    void flow() throws Exception {
        var queueCount = new CountDownLatch(1);
        var last = new AtomicReference<Execution>();

        executionQueue.addListener(execution -> {
            last.set(execution);
            queueCount.countDown();
            assertThat(execution.getFlowId(), is("realtime"));
        });

        repositoryLoader.load(
            Objects.requireNonNull(
                RealtimeTriggerTest.class.getClassLoader().getResource("flows/realtime.yaml")
            )
        );

        var task = Publish.builder()
            .id(RealtimeTriggerTest.class.getSimpleName())
            .type(Publish.class.getName())
            .server(Property.ofValue("tcp://localhost:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue("test/realtime/trigger"))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .from(
                Map.of(
                    "message", "hello trigger",
                    "notification", "Trigger is completed"
                )
            )
            .build();

        task.run(runContextFactory.of(Map.of()));
        assertThat(queueCount.await(1, TimeUnit.MINUTES), is(true));

        var payload = (Map<String, String>) last.get().getTrigger().getVariables().get("payload");
        assertThat(payload.size(), is(2));
        assertThat(payload.get("message"), is("hello trigger"));
        assertThat(payload.get("notification"), is("Trigger is completed"));
    }
}
