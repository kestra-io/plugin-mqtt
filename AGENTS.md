# Kestra MQTT Plugin

## What

- Provides plugin components under `io.kestra.plugin.mqtt`.
- Includes classes such as `Subscribe`, `Trigger`, `Publish`, `RealtimeTrigger`.

## Why

- What user problem does this solve? Teams need to publish, subscribe, and trigger workflows with MQTT from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps MQTT steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on MQTT.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `mqtt`

Infrastructure dependencies (Docker Compose services):

- `mosquitto`

### Key Plugin Classes

- `io.kestra.plugin.mqtt.Publish`
- `io.kestra.plugin.mqtt.RealtimeTrigger`
- `io.kestra.plugin.mqtt.Subscribe`
- `io.kestra.plugin.mqtt.Trigger`

### Project Structure

```
plugin-mqtt/
├── src/main/java/io/kestra/plugin/mqtt/services/
├── src/test/java/io/kestra/plugin/mqtt/services/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
