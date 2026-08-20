# How to use the MQTT plugin

Publish and subscribe to MQTT topics from Kestra flows, with support for MQTT v3 and v5.

## Common properties

Set `server` to the broker URI (`tcp://` for plain, `ssl://` for TLS) and `clientId` to a unique client identifier. For authenticated brokers, set `username` and `password`. For TLS, set `crt` to the CA certificate (PEM content, a `kestra://` URI, or a file path). Set `mqttVersion` to `V3` or `V5` (default `V5`). Store credentials in [secrets](https://kestra.io/docs/concepts/secret) and apply them globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`Publish` sends messages to a `topic` — pass messages via `from`, set `serdeType` (`JSON`, `STRING`, or `BYTES`), and control delivery with `qos` (0, 1, or 2; default 1). Set `retain: true` to have the broker retain the last message for new subscribers.

On MQTT 5, `Publish` also accepts the two request/response message properties: `responseTopic`, the topic a responder should reply to, and `correlationData`, opaque data the responder echoes back so a reply can be matched to its request. Correlation data is binary on the wire, so `correlationData` is Base64-encoded — pass a text value as `{{ 'my-id' | base64encode }}`. Both are ignored on MQTT 3.1.1, which has no message properties.

`Subscribe` reads from one or more topics set in `topic`. Bound the batch with `maxRecords` or `maxDuration`. Match `serdeType` to the publisher's format.

Each message read by `Subscribe`, `Trigger` and `RealtimeTrigger` carries `topic`, `qos`, `retain`, `payload`, and — on MQTT 5 — `responseTopic` and `correlationData` when the message set them, both `null` otherwise. `correlationData` is surfaced in the same Base64 encoding `Publish` expects, so a flow can answer a request by publishing to `{{ trigger.responseTopic }}` with `correlationData: "{{ trigger.correlationData }}"` and the bytes round-trip unchanged.

`Trigger` polls on a schedule (default 60 seconds) and starts one execution per batch. `RealtimeTrigger` starts one execution per message as it arrives.
