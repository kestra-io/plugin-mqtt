# How to use the MQTT plugin

Publish and subscribe to MQTT topics from Kestra flows, with support for MQTT v3 and v5.

## Common properties

Set `server` to the broker URI (`tcp://` for plain, `ssl://` for TLS) and `clientId` to a unique client identifier. For authenticated brokers, set `username` and `password`. For TLS, set `crt` to the CA certificate (PEM content, a `kestra://` URI, or a file path). Set `mqttVersion` to `V3` or `V5` (default `V5`). Store credentials in [secrets](https://kestra.io/docs/concepts/secret) and apply them globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`Publish` sends messages to a `topic` — pass messages via `from`, set `serdeType` (`JSON`, `STRING`, or `BYTES`), and control delivery with `qos` (0, 1, or 2; default 1). Set `retain: true` to have the broker retain the last message for new subscribers.

`Subscribe` reads from one or more topics set in `topic`. Bound the batch with `maxRecords` or `maxDuration`. Match `serdeType` to the publisher's format.

`Trigger` polls on a schedule (default 60 seconds) and starts one execution per batch. `RealtimeTrigger` starts one execution per message as it arrives.
