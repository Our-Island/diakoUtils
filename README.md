# diakoUtils

A server-side utility mod for survival servers.

`diakoUtils` provides a small module system for server-side utility features. Modules are disabled by default, can be
configured from a TOML file, and can be enabled or disabled at runtime with the `/diako` command.

Repository: <https://github.com/Our-Island/diakoUtils>

## Features

- Runtime module management through `/diako`
- TOML configuration in the Minecraft `config/` directory
- Optional LuckPerms permission support
- Vanilla permission fallback when LuckPerms is not installed
- Server-side module architecture for adding more utilities later

## Included modules

### Message Hider

Module ID: `message_hider`

Controls message visibility on the server.

Current options:

- Hide public player chat broadcasts
- Optionally hide player join and leave messages

### Entities Monitor

Module ID: `entities_monitor`

Monitors the total entity count across loaded server levels and broadcasts a warning when the count exceeds the
configured threshold.

Current options:

- Entity count threshold
- Check interval in ticks
- Warning cooldown in ticks
- Overlay/actionbar output toggle
- Custom warning message template

## Optional integrations

### LuckPerms

LuckPerms is optional. When LuckPerms is installed, `diakoUtils` checks LuckPerms permission nodes through the LuckPerms
API. When LuckPerms is not installed or its API is unavailable, `diakoUtils` falls back to the vanilla gamemaster
command permission level.

## Commands

Base command:

```mcfunction
/diako
```

Available subcommands:

```mcfunction
/diako list
/diako status <module>
/diako enable <module>
/diako disable <module>
/diako reload
```

The `<module>` argument suggests registered module IDs, such as:

```text
message_hider
entities_monitor
```

## Permissions

When LuckPerms is installed, use these permission nodes:

| Permission                   | Description                                   |
|------------------------------|-----------------------------------------------|
| `diakoutils.command`         | Grants access to all `/diako` command actions |
| `diakoutils.command.list`    | Grants `/diako list`                          |
| `diakoutils.command.status`  | Grants `/diako status <module>`               |
| `diakoutils.command.enable`  | Grants `/diako enable <module>`               |
| `diakoutils.command.disable` | Grants `/diako disable <module>`              |
| `diakoutils.command.reload`  | Grants `/diako reload`                        |

Example LuckPerms commands:

```mcfunction
/lp group admin permission set diakoutils.command true
/lp group moderator permission set diakoutils.command.list true
/lp group moderator permission set diakoutils.command.status true
```

## Configuration

Configuration file:

```text
config/diakoutils.toml
```

All modules are disabled by default. The file is created automatically on first run.

Example configuration:

```toml
[modules.message_hider]
enabled = false
hide_public_chat = true
hide_join_leave_messages = false

[modules.entities_monitor]
enabled = false
threshold = 800
check_interval_ticks = 100
cooldown_ticks = 200
overlay = false
message_template = "[EntitiesMonitor] TOO MANY ENTITIES!!!!! {count} (Threshold {threshold})"
```

### Message template placeholders

The `entities_monitor.message_template` option supports these placeholders:

| Placeholder   | Description                 |
|---------------|-----------------------------|
| `{count}`     | Current total entity count  |
| `{threshold}` | Configured entity threshold |

## Development

This project targets Minecraft 26.1, which uses unobfuscated classes and does not use Yarn mappings.

Build the project with:

```bash
./gradlew build
```

Run a development client with:

```bash
./gradlew runClient
```

Run a development server with:

```bash
./gradlew runServer
```

The built mod jar is produced by the normal `jar` task under:

```text
build/libs/
```

## Project layout

```text
src/main/java/top/ourisland/diakoutils/
├── DiakoUtils.java
├── IModule.java
├── ModuleManager.java
├── TickingModule.java
├── command/
├── config/
├── modules/
│   ├── entitiesmonitor/
│   └── messagehider/
└── permissions/
```

## Adding a new module

1. Create a class implementing `IModule`, or extend `AbstractModule`.
2. Implement `id()`, `displayName()`, `description()`, `loadConfig(...)`, and `saveConfig(...)`.
3. Implement `TickingModule` if the module needs server tick callbacks.
4. Register the module in `DiakoUtils#onInitialize()`.
5. Add any required mixins to `diakoutils.mixins.json`.

## Feedback

Please use GitHub Issues for bug reports and feature requests. When reporting a bug, include the mod version, the
Minecraft version, the Fabric version, the Fabric API version, your configuration files, and the relevant console logs
so the issue can be reproduced.

## Contributing

Contributions are welcome. Fork the repository, create a feature branch, and keep changes focused and easy to review.  
When opening a Pull Request, explain what changed, why it changed, and how to test it.

You also need to follow certain commit and Pull Request rules. We use
the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0-beta.4/) for commits and Pull Requests naming.

## License

This project is licensed under the MIT License. See [`LICENSE.txt`](LICENSE.txt) for details.
