package io.kestra.plugin.mqtt;

import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
            .from(
                List.of(
                    Map.of(
                        "message", "{{ \"apple\" ~ \"pear\" ~ \"banana\" }}"
                    )
                )
            )
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

        List<Map<String, Object>> result;
        try (var inputStream = new BufferedInputStream(storageInterface.get(TenantService.MAIN_TENANT, null, subscribeOutput.getUri()), FileSerde.BUFFER_SIZE)) {
            result = FileSerde.readAll(inputStream, Map.class).map(m -> (Map<String, Object>) m).collectList().block();
        }

        assertThat(result.size(), is(1));

        assertThat(result.get(0).get("topic"), is("test/" + topic));
        assertThat(result.get(0).get("qos"), is(1));
        assertThat(result.get(0).get("retain"), is(true));

        Map<String, Object> value = (Map<String, Object>) result.get(0).get("payload");
        assertThat(value.get("message"), is("applepearbanana"));
    }

    @Test
    void stringPayloadListShouldPublishMultipleMessages() throws Exception {
        var runContext = runContextFactory.of(Map.of());

        String topic = IdUtils.create();
        String server = "tcp://127.0.0.1:1883";

        Publish publish = Publish.builder()
            .server(Property.ofValue(server))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue("test/" + topic))
            .serdeType(Property.ofValue(SerdeType.STRING))
            .retain(Property.ofValue(false))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V3))
            .from(List.of("1", "2"))
            .build();

        Publish.Output publishOutput = publish.run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(2));
    }

    @Test
    void v5ShouldRoundTripResponseTopicAndCorrelationData() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = "test/" + IdUtils.create();
        String correlationData = base64("correlation-1");

        Publish.Output publishOutput = publish(AbstractMqttConnection.Version.V5, topic, topic + "/reply", correlationData)
            .run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(1));

        Map<String, Object> message = firstMessage(runContext, AbstractMqttConnection.Version.V5, topic);

        assertThat(message.get("responseTopic"), is(topic + "/reply"));
        assertThat(message.get("correlationData"), is(correlationData));
    }

    @Test
    void v5ShouldRoundTripResponseTopicWithoutCorrelationData() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = "test/" + IdUtils.create();

        Publish.Output publishOutput = publish(AbstractMqttConnection.Version.V5, topic, topic + "/reply", null)
            .run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(1));

        Map<String, Object> message = firstMessage(runContext, AbstractMqttConnection.Version.V5, topic);

        assertThat(message.get("responseTopic"), is(topic + "/reply"));
        assertThat(message.get("correlationData"), is(nullValue()));
    }

    @Test
    void v5ShouldRoundTripCorrelationDataWithoutResponseTopic() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = "test/" + IdUtils.create();
        String correlationData = base64("correlation-1");

        Publish.Output publishOutput = publish(AbstractMqttConnection.Version.V5, topic, null, correlationData)
            .run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(1));

        Map<String, Object> message = firstMessage(runContext, AbstractMqttConnection.Version.V5, topic);

        assertThat(message.get("responseTopic"), is(nullValue()));
        assertThat(message.get("correlationData"), is(correlationData));
    }

    @Test
    void v3ShouldIgnoreMessageProperties() throws Exception {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = "test/" + IdUtils.create();

        Publish.Output publishOutput = publish(AbstractMqttConnection.Version.V3, topic, topic + "/reply", base64("correlation-1"))
            .run(runContext);

        assertThat(publishOutput.getMessagesCount(), is(1));

        Map<String, Object> message = firstMessage(runContext, AbstractMqttConnection.Version.V3, topic);

        assertThat(message.get("responseTopic"), is(nullValue()));
        assertThat(message.get("correlationData"), is(nullValue()));
    }

    @Test
    void shouldFailWithCorrelationDataThatIsNotBase64() {
        RunContext runContext = runContextFactory.of(ImmutableMap.of());
        String topic = "test/" + IdUtils.create();

        Publish publish = publish(AbstractMqttConnection.Version.V5, topic, null, "not base64!");

        assertThrows(IllegalArgumentException.class, () -> publish.run(runContext));
    }

    private Publish publish(AbstractMqttConnection.Version version, String topic, String responseTopic, String correlationData) {
        return Publish.builder()
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue(topic))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(version))
            .responseTopic(responseTopic == null ? null : Property.ofValue(responseTopic))
            .correlationData(correlationData == null ? null : Property.ofValue(correlationData))
            .from(List.of(Map.of("message", "with message properties")))
            .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMessage(RunContext runContext, AbstractMqttConnection.Version version, String topic) throws Exception {
        Subscribe.Output subscribeOutput = Subscribe.builder()
            .server(Property.ofValue("tcp://127.0.0.1:1883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(topic)
            .serdeType(Property.ofValue(SerdeType.JSON))
            .maxRecords(Property.ofValue(1))
            .mqttVersion(Property.ofValue(version))
            .build()
            .run(runContext);

        try (var inputStream = new BufferedInputStream(storageInterface.get(TenantService.MAIN_TENANT, null, subscribeOutput.getUri()), FileSerde.BUFFER_SIZE)) {
            List<Map<String, Object>> rows = FileSerde.readAll(inputStream, Map.class)
                .map(m -> (Map<String, Object>) m)
                .collectList()
                .block();

            assertThat(rows.size(), is(1));

            return rows.getFirst();
        }
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
        var resource = SuiteTest.class.getClassLoader().getResource("crt/ca.crt");
        this.run(AbstractMqttConnection.Version.V3, resource.toURI().getPath());
    }

    @Test
    void v5SSL() throws Exception {
        var resource = SuiteTest.class.getClassLoader().getResource("crt/ca.crt");
        this.run(AbstractMqttConnection.Version.V5, resource.toURI().getPath());
    }

    @Test
    void v3SSLWithPemContent() throws Exception {
        var resource = SuiteTest.class.getClassLoader().getResource("crt/ca.crt");
        var pemContent = Files.readString(Path.of(resource.toURI()));
        this.run(AbstractMqttConnection.Version.V3, pemContent);
    }

    @Test
    void v5SSLWithPemContent() throws Exception {
        var resource = SuiteTest.class.getClassLoader().getResource("crt/ca.crt");
        var pemContent = Files.readString(Path.of(resource.toURI()));
        this.run(AbstractMqttConnection.Version.V5, pemContent);
    }

    @Test
    void shouldFailWithInvalidCrt() {
        var runContext = runContextFactory.of(ImmutableMap.of());
        var topic = IdUtils.create();

        var publish = Publish.builder()
            .server(Property.ofValue("ssl://127.0.0.1:8883"))
            .clientId(Property.ofValue(IdUtils.create()))
            .topic(Property.ofValue("test/" + topic))
            .serdeType(Property.ofValue(SerdeType.JSON))
            .retain(Property.ofValue(true))
            .mqttVersion(Property.ofValue(AbstractMqttConnection.Version.V5))
            .crt(Property.ofValue("not-a-valid-cert-or-path"))
            .from(List.of(Map.of("key", "value")))
            .build();

        assertThrows(IllegalArgumentException.class, () -> publish.run(runContext));
    }
}
