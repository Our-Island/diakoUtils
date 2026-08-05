package top.ourisland.diakoutils.property;

import com.electronwill.nightconfig.core.Config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class PropertyCodecs {

    private static final PropertyCodec<Boolean> BOOLEAN = new PropertyCodec<>() {
        @Override
        public Boolean parse(String input) throws PropertyParseException {
            if ("true".equalsIgnoreCase(input)) {
                return true;
            }
            if ("false".equalsIgnoreCase(input)) {
                return false;
            }

            throw new PropertyParseException(
                    "Invalid boolean: " + input + ". Expected true or false."
            );
        }

        @Override
        public Boolean read(
                Config config,
                String path,
                Boolean fallback
        ) throws PropertyParseException {
            var value = config.get(path);
            if (value == null) {
                return fallback;
            }

            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }

            throw invalidConfigType(path, "boolean", value);
        }

        @Override
        public void write(
                Config config,
                String path,
                Boolean value
        ) {
            config.set(path, value);
        }

        @Override
        public String format(Boolean value) {
            return value.toString();
        }

        @Override
        public String typeName() {
            return "boolean";
        }

        @Override
        public Collection<String> suggestions() {
            return List.of("true", "false");
        }
    };

    private static final PropertyCodec<Integer> INTEGER = new PropertyCodec<>() {
        @Override
        public Integer parse(String input) throws PropertyParseException {
            try {
                return Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                throw new PropertyParseException("Invalid integer: " + input, e);
            }
        }

        @Override
        public Integer read(
                Config config,
                String path,
                Integer fallback
        ) throws PropertyParseException {
            var value = config.get(path);
            if (value == null) {
                return fallback;
            }

            var parsed = exactLong(path, value);
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
                throw new PropertyParseException(
                        "Value at " + path + " is outside the integer range: " + value
                );
            }

            return (int) parsed;
        }

        @Override
        public void write(
                Config config,
                String path,
                Integer value
        ) {
            config.set(path, value);
        }

        @Override
        public String format(Integer value) {
            return value.toString();
        }

        @Override
        public String typeName() {
            return "integer";
        }
    };

    private static final PropertyCodec<Long> LONG = new PropertyCodec<>() {
        @Override
        public Long parse(String input) throws PropertyParseException {
            try {
                return Long.parseLong(input.trim());
            } catch (NumberFormatException e) {
                throw new PropertyParseException("Invalid long integer: " + input, e);
            }
        }

        @Override
        public Long read(
                Config config,
                String path,
                Long fallback
        ) throws PropertyParseException {
            var value = config.get(path);
            if (value == null) {
                return fallback;
            }

            return exactLong(path, value);
        }

        @Override
        public void write(
                Config config,
                String path,
                Long value
        ) {
            config.set(path, value);
        }

        @Override
        public String format(Long value) {
            return value.toString();
        }

        @Override
        public String typeName() {
            return "long";
        }
    };

    private static final PropertyCodec<Float> FLOAT = new PropertyCodec<>() {
        @Override
        public Float parse(String input) throws PropertyParseException {
            try {
                var value = Float.parseFloat(input.trim());
                if (!Float.isFinite(value)) {
                    throw new NumberFormatException("non-finite");
                }

                return value;
            } catch (NumberFormatException e) {
                throw new PropertyParseException("Invalid float: " + input, e);
            }
        }

        @Override
        public Float read(
                Config config,
                String path,
                Float fallback
        ) throws PropertyParseException {
            var value = config.get(path);
            if (value == null) {
                return fallback;
            }

            if (value instanceof Number number) {
                var parsed = number.floatValue();
                if (Float.isFinite(parsed)) {
                    return parsed;
                }
            }

            throw invalidConfigType(path, "number", value);
        }

        @Override
        public void write(
                Config config,
                String path,
                Float value
        ) {
            config.set(path, value.doubleValue());
        }

        @Override
        public String format(Float value) {
            return value.toString();
        }

        @Override
        public String typeName() {
            return "float";
        }
    };

    private static final PropertyCodec<Double> DOUBLE = new PropertyCodec<>() {
        @Override
        public Double parse(String input) throws PropertyParseException {
            try {
                var value = Double.parseDouble(input.trim());
                if (!Double.isFinite(value)) {
                    throw new NumberFormatException("non-finite");
                }

                return value;
            } catch (NumberFormatException e) {
                throw new PropertyParseException("Invalid number: " + input, e);
            }
        }

        @Override
        public Double read(
                Config config,
                String path,
                Double fallback
        ) throws PropertyParseException {
            var value = config.get(path);
            if (value == null) {
                return fallback;
            }

            if (value instanceof Number number) {
                var parsed = number.doubleValue();
                if (Double.isFinite(parsed)) {
                    return parsed;
                }
            }

            throw invalidConfigType(path, "number", value);
        }

        @Override
        public void write(
                Config config,
                String path,
                Double value
        ) {
            config.set(path, value);
        }

        @Override
        public String format(Double value) {
            return value.toString();
        }

        @Override
        public String typeName() {
            return "number";
        }
    };

    private static final PropertyCodec<String> STRING = new PropertyCodec<>() {
        @Override
        public String parse(String input) {
            return input;
        }

        @Override
        public String read(
                Config config,
                String path,
                String fallback
        ) throws PropertyParseException {
            var value = config.get(path);
            if (value == null) {
                return fallback;
            }

            if (value instanceof String stringValue) {
                return stringValue;
            }

            throw invalidConfigType(path, "string", value);
        }

        @Override
        public void write(
                Config config,
                String path,
                String value
        ) {
            config.set(path, value);
        }

        @Override
        public String format(String value) {
            return value;
        }

        @Override
        public String typeName() {
            return "string";
        }
    };

    private PropertyCodecs() {
    }

    public static PropertyCodec<?> find(Class<?> type) {
        return switch (type) {
            case Class<?> t when t == boolean.class || t == Boolean.class -> BOOLEAN;
            case Class<?> t when t == int.class || t == Integer.class -> INTEGER;
            case Class<?> t when t == long.class || t == Long.class -> LONG;
            case Class<?> t when t == float.class || t == Float.class -> FLOAT;
            case Class<?> t when t == double.class || t == Double.class -> DOUBLE;
            case Class<?> t when t == String.class -> STRING;
            case Class<?> t when t.isEnum() -> enumCodec(t);
            default -> null;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PropertyCodec<?> enumCodec(Class<?> type) {
        var enumType = (Class<? extends Enum>) type;
        var constants = enumType.getEnumConstants();
        return new PropertyCodec<Enum>() {
            @Override
            public Enum parse(String input) throws PropertyParseException {
                return Arrays.stream(constants)
                        .filter(value ->
                                value.name().equalsIgnoreCase(input.trim())
                        )
                        .findFirst()
                        .orElseThrow(() -> new PropertyParseException(
                                "Invalid %s: %s. Expected one of: %s".formatted(
                                        type.getSimpleName(),
                                        input,
                                        String.join(", ", suggestions())
                                )
                        ));
            }

            @Override
            public Enum read(
                    Config config,
                    String path,
                    Enum fallback
            ) throws PropertyParseException {
                var value = config.get(path);
                if (value == null) {
                    return fallback;
                }

                if (value instanceof String stringValue) {
                    return parse(stringValue);
                }

                throw invalidConfigType(path, "string", value);
            }

            @Override
            public void write(
                    Config config,
                    String path,
                    Enum value
            ) {
                config.set(path, format(value));
            }

            @Override
            public String format(Enum value) {
                return value.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public String typeName() {
                return "enum";
            }

            @Override
            public Collection<String> suggestions() {
                return Arrays.stream(constants)
                        .map(value ->
                                value.name().toLowerCase(Locale.ROOT)
                        )
                        .toList();
            }
        };
    }

    private static PropertyParseException invalidConfigType(
            String path,
            String expected,
            Object value
    ) {
        return new PropertyParseException(
                "Expected %s at %s, got %s (%s)".formatted(
                        expected,
                        path,
                        value.getClass().getSimpleName(),
                        value
                )
        );
    }

    private static long exactLong(String path, Object value) throws PropertyParseException {
        if (!(value instanceof Number number)) {
            throw invalidConfigType(path, "integer", value);
        }

        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new PropertyParseException(
                    "Expected an integer at %s, got %s".formatted(path, value),
                    e
            );
        }
    }

}
