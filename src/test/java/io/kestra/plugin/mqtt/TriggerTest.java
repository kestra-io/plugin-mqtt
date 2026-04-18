package io.kestra.plugin.mqtt;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.EvaluateTrigger;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.mqtt.services.SerdeType;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

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
}
