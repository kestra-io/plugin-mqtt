package io.kestra.plugin.mqtt;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.mqtt.services.SerdeType;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class SuiteTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @SuppressWarnings("unchecked")
    void run(AbstractMqttConnection.Version version) throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = IdUtils.create();

        Publish publish = Publish.builder()
            .server("tcp://localhost:1883")
            .clientId(IdUtils.create())
            .topic("test/" + topic)
            .serdeType(SerdeType.JSON)
            .retain(true)
            .version(version)
            .from(List.of(Map.of(
                "message", "{{ \"apple\" ~ \"pear\" ~ \"banana\" }}"
            )))
            .build();

        Publish.Output publishOutput = publish.run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(1));

        Subscribe subscribe = Subscribe.builder()
            .server("tcp://localhost:1883")
            .clientId(IdUtils.create())
            .topic("test/" + topic)
            .serdeType(SerdeType.JSON)
            .maxRecords(1)
            .version(version)
            .build();
        Subscribe.Output subscribeOutput = subscribe.run(runContext);

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(null, subscribeOutput.getUri())));
        List<Map<String, Object>> result = new ArrayList<>();
        FileSerde.reader(inputStream, r -> result.add((Map<String, Object>) r));

        assertThat(result.size(), is(1));

        assertThat(result.get(0).get("topic"), is("test/" + topic));
        assertThat(result.get(0).get("qos"), is(1));
        assertThat(result.get(0).get("retain"), is(true));

        Map<String, Object> value = (Map<String, Object>) result.get(0).get("payload");
        assertThat(value.get("message"), is("applepearbanana"));
    }

    @Test
    void v3() throws Exception {
        this.run(AbstractMqttConnection.Version.V3);
    }

    @Test
    void v5() throws Exception {
        this.run(AbstractMqttConnection.Version.V5);
    }
}
