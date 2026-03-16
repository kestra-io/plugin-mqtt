package io.kestra.plugin.mqtt.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLSocketFactory;

import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageContext;
import io.kestra.plugin.mqtt.AbstractMqttConnection;
import io.kestra.plugin.mqtt.MqttConnectionInterface;

public abstract class MqttFactory {

    private MqttFactory() {
        // utility class
    }

    public static MqttInterface create(RunContext runContext, MqttConnectionInterface connection) throws Exception {
        var sslSocketFactory = resolveSSLSocketFactory(runContext, connection);

        var version = runContext.render(connection.getMqttVersion()).as(AbstractMqttConnection.Version.class).orElseThrow();
        if (version == AbstractMqttConnection.Version.V5) {
            var service = new MqttV5Service();
            service.connect(runContext, (AbstractMqttConnection) connection, sslSocketFactory);
            return service;
        } else {
            var service = new MqttV3Service();
            service.connect(runContext, (AbstractMqttConnection) connection, sslSocketFactory);
            return service;
        }
    }

    private static SSLSocketFactory resolveSSLSocketFactory(RunContext runContext, MqttConnectionInterface connection) throws Exception {
        if (connection.getCrt() == null) {
            return null;
        }

        var rCrt = runContext.render(connection.getCrt()).as(String.class).orElse(null);
        if (rCrt == null || rCrt.isBlank()) {
            return null;
        }

        try (var inputStream = resolveInputStream(runContext, rCrt)) {
            return CustomSSLSocketFactory.createSSLSocketFactory(inputStream);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(
                "Failed to load CA certificate: the provided certificate content is invalid. " +
                "Ensure it is a valid PEM-encoded X.509 certificate.",
                e
            );
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Failed to read CA certificate: " + e.getMessage(),
                e
            );
        }
    }

    private static InputStream resolveInputStream(RunContext runContext, String crt) throws Exception {
        // Internal storage URI (e.g. kestra:///...)
        if (crt.startsWith(StorageContext.KESTRA_PROTOCOL)) {
            return runContext.storage().getFile(URI.create(crt));
        }

        // Local file path
        var path = Path.of(crt);
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }

        // PEM content (starts with the PEM header)
        if (crt.startsWith("-----BEGIN ")) {
            return new ByteArrayInputStream(crt.getBytes(StandardCharsets.UTF_8));
        }

        throw new IllegalArgumentException(
            "Cannot resolve CA certificate: the value is not a valid Kestra internal storage URI, " +
            "an existing file path, or PEM-encoded certificate content. Received: " +
            crt.substring(0, Math.min(crt.length(), 50)) + (crt.length() > 50 ? "..." : "")
        );
    }
}
