package top.ourisland.diakoutils.property;

import com.electronwill.nightconfig.core.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class StringListPropertyCodec implements PropertyCodec<List<String>> {

    @Override
    public List<String> parse(String input) throws PropertyParseException {
        return normalize(
                List.of(input.split(",", -1)),
                "command value"
        );
    }

    @Override
    public List<String> read(
            Config config,
            String path,
            List<String> fallback
    ) throws PropertyParseException {
        var value = config.get(path);
        if (value == null) {
            return fallback;
        }

        if (!(value instanceof List<?> list)) {
            throw new PropertyParseException(
                    "Expected string array at %s, got %s (%s)".formatted(
                            path,
                            value.getClass().getSimpleName(),
                            value
                    )
            );
        }

        var strings = new ArrayList<String>(list.size());
        for (var entry : list) {
            if (!(entry instanceof String stringValue)) {
                throw new PropertyParseException(
                        "Expected string entries at %s, got %s (%s)".formatted(
                                path,
                                entry == null
                                        ? "null"
                                        : entry.getClass().getSimpleName(),
                                entry
                        )
                );
            }
            strings.add(stringValue);
        }

        return normalize(strings, path);
    }

    @Override
    public void write(
            Config config,
            String path,
            List<String> value
    ) {
        config.set(path, new ArrayList<>(value));
    }

    @Override
    public String format(List<String> value) {
        return String.join(", ", value);
    }

    @Override
    public String typeName() {
        return "string list";
    }

    private static List<String> normalize(
            Collection<String> values,
            String source
    ) throws PropertyParseException {
        var normalized = new LinkedHashSet<String>();
        for (var value : values) {
            var trimmed = value == null
                    ? ""
                    : value.trim();
            if (trimmed.isEmpty()) {
                throw new PropertyParseException(
                        "String list at " + source + " must not contain empty entries."
                );
            }
            normalized.add(trimmed);
        }
        return List.copyOf(normalized);
    }

}
