package io.kestra.plugin.mqtt;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.mqtt.services.SerdeType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class SuiteTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private StorageInterface storageInterface;

    @SuppressWarnings("unchecked")
    void run(AbstractMqttConnection.Version version, String caUri) throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = IdUtils.create();

        String server = "tcp://127.0.0.1:1883";

        if (caUri != null) {
            server = "ssl://127.0.0.1:8883";
        }

        Publish publish = Publish.builder()
            .server(Property.ofValue(server))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue("test/" + topic))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(version))
            .crt(Property.ofValue(caUri))
            .from(List.of(Map.of(
                "message", "{{ \"apple\" ~ \"pear\" ~ \"banana\" }}"
            )))
            .build();

        Publish.Output publishOutput = publish.run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(1));

        Subscribe subscribe = Subscribe.builder()
            .server(Property.ofValue(server))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic("test/" + topic)
            .serdeType(Property.ofValue(SerdeType.JSON))
            .maxRecords(Property.ofValue(1))
            .mqttVersion(Property.ofValue(version))
            .crt(Property.ofValue(caUri))
            .build();
        Subscribe.Output subscribeOutput = subscribe.run(runContext);

        BufferedReader inputStream = new BufferedReader(new InputStreamReader(storageInterface.get(TenantService.MAIN_TENANT, null, subscribeOutput.getUri())));
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
        this.run(AbstractMqttConnection.Version.V3, null);
    }

    @Test
    void v5() throws Exception {
        this.run(AbstractMqttConnection.Version.V5, null);
    }

    @Test
    void v3SSL() throws Exception {
        URL resource = SuiteTest.class.getClassLoader().getResource("crt/ca.crt");
        this.run(AbstractMqttConnection.Version.V3, resource.toURI().getPath());
    }

    @Test
    void v5SSL() throws Exception {
        URL resource = SuiteTest.class.getClassLoader().getResource("crt/ca.crt");
        this.run(AbstractMqttConnection.Version.V5, resource.toURI().getPath());
    }
}
