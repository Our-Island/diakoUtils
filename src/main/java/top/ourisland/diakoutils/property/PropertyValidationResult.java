package top.ourisland.diakoutils.property;

public record PropertyValidationResult(
        boolean valid,
        String message
) {

    public static PropertyValidationResult success() {
        return new PropertyValidationResult(true, "");
    }

    public static PropertyValidationResult failure(String message) {
        return new PropertyValidationResult(false, message);
    }

}
