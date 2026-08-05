# diakoUtils

A server-side utility mod for survival servers.

`diakoUtils` provides a small module system for server-side utility features. Modules are disabled by default, can be
configured from a TOML file, and can be enabled or disabled at runtime with the `/diako` command.

Repository: <https://github.com/Our-Island/diakoUtils>

## Features

- Runtime module and property management through `/diako`
- Annotation-driven, validated TOML configuration in the Minecraft `config/` directory
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
/diako config <module>
/diako config <module> list
/diako config <module> get <property>
/diako config <module> set <property> <value>
/diako config <module> reset <property>
/diako config <module> reset-all
/diako reload
```

The `<module>` argument suggests registered module IDs. Property commands also suggest property IDs and common values
such as booleans, enum values, defaults, and configured numeric limits. String values use a greedy argument, so values
containing spaces do not need quotes.

## Permissions

When LuckPerms is installed, use these permission nodes:

| Permission                        | Description                                      |
|-----------------------------------|--------------------------------------------------|
| `diakoutils.command`              | Grants access to all `/diako` command actions    |
| `diakoutils.command.list`         | Grants `/diako list`                             |
| `diakoutils.command.status`       | Grants `/diako status <module>`                  |
| `diakoutils.command.enable`       | Grants `/diako enable <module>`                  |
| `diakoutils.command.disable`      | Grants `/diako disable <module>`                 |
| `diakoutils.command.reload`       | Grants `/diako reload`                           |
| `diakoutils.command.config`       | Grants all module property commands              |
| `diakoutils.command.config.get`   | Grants property `list` and `get` commands        |
| `diakoutils.command.config.set`   | Grants the property `set` command                |
| `diakoutils.command.config.reset` | Grants property `reset` and `reset-all` commands |

Example LuckPerms commands:

```mcfunction
/lp group admin permission set diakoutils.command true
/lp group moderator permission set diakoutils.command.list true
/lp group moderator permission set diakoutils.command.status true
/lp group moderator permission set diakoutils.command.config.get true
```

## Configuration

Configuration file:

```text
config/diakoutils.toml
```

All modules are disabled by default. The file is created automatically on first run. Module properties are declared with
`@ModuleProperty`, loaded and validated atomically per module, and written to their existing TOML paths.

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

Property changes made through `/diako config` are applied immediately and persisted. If saving fails, the in-memory
value is rolled back. `/diako reload` validates a complete candidate property set before applying it, so a module is
never left partially updated.

### Message template placeholders

The `entities_monitor.message_template` option supports these placeholders:

| Placeholder   | Description                 |
|---------------|-----------------------------|
| `{count}`     | Current total entity count  |
| `{threshold}` | Configured entity threshold |

The template must contain `{count}`.

## Development

This project targets Minecraft 26.2, which uses unobfuscated classes and does not use Yarn mappings.

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
├── annotation/
├── command/
├── config/
├── discovery/
├── property/
├── modules/
│   ├── entitiesmonitor/
│   └── messagehider/
└── permissions/
```

## Adding a new module

1. Create a public, non-abstract class implementing `IModule`, or extend `AbstractModule`.
2. Add `@DiakoModule` to the class and place it under `top.ourisland.diakoutils.modules` or one of its subpackages.
3. Add configurable, non-static, non-final fields and annotate them with `@ModuleProperty`.
4. Implement `TickingModule` if the module needs server tick callbacks.
5. Override `validateProperties(...)` for rules involving multiple properties.
6. Override `onPropertiesChanged(...)` only when runtime caches or counters must be refreshed.
7. Add any required mixins to `diakoutils.mixins.json`.

Example:

```java
@DiakoModule(
        id = "example_module",
        displayName = "Example Module",
        description = "An automatically discovered module."
)
public final class ExampleModule extends AbstractModule {

    @ModuleProperty(
            id = "threshold",
            displayName = "Threshold",
            description = "Example numeric threshold.",
            min = "0",
            max = "1000"
    )
    private final int threshold = 100;

}
```

The framework automatically discovers the module and property, captures the field initializer as its default value, adds
command completion, validates command and TOML input, and persists the property at `modules.example_module.threshold`.

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
