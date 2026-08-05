package top.ourisland.diakoutils.annotation;

import java.lang.annotation.*;

/**
 * Declares a discoverable diakoUtils module and its user-facing metadata.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DiakoModule {

    String id();

    String displayName();

    String description() default "";

    /**
     * Controls deterministic registration order. Lower values are registered first.
     */
    int order() default 0;

}
