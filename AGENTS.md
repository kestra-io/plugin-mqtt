# Kestra MQTT Plugin

## What

- Provides plugin components under `io.kestra.plugin.mqtt`.
- Includes classes such as `Subscribe`, `Trigger`, `Publish`, `RealtimeTrigger`.

## Why

- This plugin integrates Kestra with MQTT.
- It provides tasks that publish, subscribe, and trigger workflows with MQTT.

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
