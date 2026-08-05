package top.ourisland.diakoutils.annotation;

import top.ourisland.diakoutils.property.PropertyApplyMode;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ModuleProperty {

    String id();

    String displayName();

    String description() default "";

    int order() default 0;

    String min() default "";

    String max() default "";

    int maxLength() default -1;

    String pattern() default "";

    String[] suggestions() default {};

    PropertyApplyMode applyMode() default PropertyApplyMode.IMMEDIATE;

}
