package io.kestra.plugin.mqtt;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Duration;

public interface MqttConnectionInterface {
    @Schema(
        title = "The address of the server to connect to, specified as a URI",
        description = "The serverURI parameter is typically used with the the clientId parameter to form a key. " +
            "The key is used to store and reference messages while they are being delivered.\n" +
            "The address of the server to connect to is specified as a URI. " +
            "Two types of connection are supported `tcp://` for a TCP connection and `ssl://` for a TCP connection secured by SSL/TLS. " +
            "For example:\n" +
            "* `tcp://localhost:1883`\n" +
            "* `ssl://localhost:8883`\n" +
            "If the port is not specified, it will default to 1883 for `tcp://`\" URIs, and 8883 for `ssl://` URIs."
    )
    @NotNull
    Property<String> getServer();

    @Schema(
        title = "The MQTT version to use."
    )
    @NotNull
    Property<AbstractMqttConnection.Version> getVersion();

    @Schema(
        title = "A client identifier that is unique on the server being connected to",
        description = "A client identifier clientId must be specified and be less that 65535 characters. It must be " +
            "unique across all clients connecting to the same server. The clientId is used by the server to store" +
            " data related to the client, hence it is important that the clientId remain the same when connecting to " +
            "a server if durable subscriptions or reliable messaging are required.\n" +
            "As the client identifier is used by the server to identify a client when it reconnects, the client " +
            "must use the same identifier between connections if durable subscriptions or reliable delivery " +
            "of messages is required."
    )
    @NotNull
    Property<String> getClientId();

    @Schema(
        title = "The connection timeout.",
        description = "This value defines the maximum time interval the client will wait for the network connection to " +
            "the MQTT server to be established. The default timeout is 30 seconds. A value of 0 disables timeout " +
            "processing meaning the client will wait until the network connection is made successfully or fails."
    )
    Property<Duration> getConnectionTimeout();

    @Schema(
        title = "Disable ssl verification.",
        description = "This value will allow all ca certificate."
    )
    Property<Boolean> getHttpsHostnameVerificationEnabled();

    @Schema(
        title = "The Authentication Method.",
        description = "Only available if `version` = `V5`\n" +
            "If set, this value contains the name of the authentication method to be used for extended " +
            "authentication. If null, extended authentication is not performed."
    )
    Property<String> getAuthMethod();

    @Schema(
        title = "The user name to use for the connection."
    )
    Property<String> getUsername();


    @Schema(
        title = "The password to use for the connection."
    )
    Property<String> getPassword();

    @Schema(
        title = "Server certificate file path."
    )
    Property<String> getCrt();
}
