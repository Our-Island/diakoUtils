package top.ourisland.diakoutils.property;

public record ModulePropertyChange<T>(
        String propertyId,
        T oldValue,
        T newValue
) {

}
