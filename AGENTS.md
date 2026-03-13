# Kestra MQTT Plugin

## What

Integrate MQTT messaging protocol into Kestra data orchestration. Exposes 4 plugin components (tasks, triggers, and/or conditions).

## Why

Enables Kestra workflows to interact with MQTT, allowing orchestration of MQTT-based operations as part of data pipelines and automation workflows.

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

### Important Commands

```bash
# Build the plugin
./gradlew shadowJar

# Run tests
./gradlew test

# Build without tests
./gradlew shadowJar -x test
```

### Configuration

All tasks and triggers accept standard Kestra plugin properties. Credentials should use
`{{ secret('SECRET_NAME') }}` — never hardcode real values.

## Agents

**IMPORTANT:** This is a Kestra plugin repository (prefixed by `plugin-`, `storage-`, or `secret-`). You **MUST** delegate all coding tasks to the `kestra-plugin-developer` agent. Do NOT implement code changes directly — always use this agent.
