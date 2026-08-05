package top.ourisland.diakoutils.property;

import top.ourisland.diakoutils.annotation.ModuleProperty;

import java.math.BigDecimal;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PropertyConstraints<T> {

    private final T minimum;
    private final T maximum;
    private final int maxLength;
    private final Pattern pattern;

    private PropertyConstraints(
            T minimum,
            T maximum,
            int maxLength,
            Pattern pattern
    ) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.maxLength = maxLength;
        this.pattern = pattern;
    }

    public static <T> PropertyConstraints<T> from(
            ModuleProperty annotation,
            Class<?> valueType,
            PropertyCodec<T> codec
    ) {
        var numeric = isNumeric(valueType);
        if ((!annotation.min().isEmpty() || !annotation.max().isEmpty()) && !numeric) {
            throw new IllegalArgumentException("min/max can only be used with numeric properties");
        }

        if (annotation.maxLength() < -1) {
            throw new IllegalArgumentException("maxLength must be -1 or greater");
        }

        if (annotation.maxLength() >= 0 && valueType != String.class) {
            throw new IllegalArgumentException("maxLength can only be used with string properties");
        }

        if (!annotation.pattern().isEmpty() && valueType != String.class) {
            throw new IllegalArgumentException("pattern can only be used with string properties");
        }

        T minimum = null;
        T maximum = null;
        try {
            if (!annotation.min().isEmpty()) {
                minimum = codec.parse(annotation.min());
            }
            if (!annotation.max().isEmpty()) {
                maximum = codec.parse(annotation.max());
            }
        } catch (PropertyParseException e) {
            throw new IllegalArgumentException("Invalid min/max value: " + e.getMessage(), e);
        }

        if (minimum != null && maximum != null
                && decimal((Number) minimum).compareTo(decimal((Number) maximum)) > 0
        ) {
            throw new IllegalArgumentException("min must not be greater than max");
        }

        Pattern pattern = null;
        if (!annotation.pattern().isEmpty()) {
            try {
                pattern = Pattern.compile(annotation.pattern());
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid property pattern: " + e.getMessage(),
                        e
                );
            }
        }

        return new PropertyConstraints<>(
                minimum,
                maximum,
                annotation.maxLength(),
                pattern
        );
    }

    private static boolean isNumeric(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class;
    }

    private static BigDecimal decimal(Number number) {
        return new BigDecimal(number.toString());
    }

    public PropertyValidationResult validate(String propertyId, T value) {
        if (value == null) {
            return PropertyValidationResult.failure(propertyId + " must not be null.");
        }

        if (value instanceof Number number) {
            var decimalValue = decimal(number);
            if (minimum instanceof Number min
                    && decimalValue.compareTo(decimal(min)) < 0
            ) {
                return rangeFailure(propertyId);
            }

            if (maximum instanceof Number max
                    && decimalValue.compareTo(decimal(max)) > 0
            ) {
                return rangeFailure(propertyId);
            }
        }

        if (value instanceof String stringValue) {
            if (maxLength >= 0 && stringValue.length() > maxLength) {
                return PropertyValidationResult.failure(
                        propertyId + " must not exceed " + maxLength + " characters."
                );
            }

            if (pattern != null && !pattern.matcher(stringValue).matches()) {
                return PropertyValidationResult.failure(
                        propertyId + " must match pattern: " + pattern.pattern()
                );
            }
        }

        return PropertyValidationResult.success();
    }

    private PropertyValidationResult rangeFailure(String propertyId) {
        if (minimum != null && maximum != null) {
            return PropertyValidationResult.failure(
                    propertyId + " must be between " + minimum + " and " + maximum + "."
            );
        }

        if (minimum != null) {
            return PropertyValidationResult.failure(
                    propertyId + " must be at least " + minimum + "."
            );
        }

        return PropertyValidationResult.failure(
                propertyId + " must be at most " + maximum + "."
        );
    }

    public String rangeDescription(PropertyCodec<T> codec) {
        if (minimum == null && maximum == null) {
            return "";
        }

        if (minimum != null && maximum != null) {
            return codec.format(minimum) + ".." + codec.format(maximum);
        }

        if (minimum != null) {
            return ">= " + codec.format(minimum);
        }

        return "<= " + codec.format(maximum);
    }

    public T minimum() {
        return minimum;
    }

    public T maximum() {
        return maximum;
    }

}
