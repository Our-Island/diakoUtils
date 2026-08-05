package top.ourisland.diakoutils.property;

import java.util.List;

public record PropertyOperationResult(
        boolean successful,
        boolean changed,
        String message,
        List<ModulePropertyChange<?>> changes
) {

    public static PropertyOperationResult success(List<ModulePropertyChange<?>> changes) {
        return new PropertyOperationResult(true, true, "", List.copyOf(changes));
    }

    public static PropertyOperationResult noChange(String message) {
        return new PropertyOperationResult(true, false, message, List.of());
    }

    public static PropertyOperationResult failure(String message) {
        return new PropertyOperationResult(false, false, message, List.of());
    }

}
