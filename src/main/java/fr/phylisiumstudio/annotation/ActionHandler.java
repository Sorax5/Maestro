package fr.phylisiumstudio.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ActionHandler {
    String event();
    Arg[] args() default {};
}
