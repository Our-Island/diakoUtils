package top.ourisland.diakoutils.annotation;

public final class ModuleMetadata {

    private ModuleMetadata() {
    }

    public static DiakoModule require(Class<?> moduleType) {
        var metadata = moduleType.getAnnotation(DiakoModule.class);
        if (metadata == null) {
            throw new IllegalStateException(
                    "Module type is missing @DiakoModule: " + moduleType.getName()
            );
        }

        return metadata;
    }

}
