package com.felipefzdz.spock;

import groovy.lang.Closure;
import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(WiremockScenarioExtension.class)
@Inherited
public @interface WiremockScenario {
    int[] ports() default {};

    String[] targets() default {};

    int replayPort() default 8080;

    String mappingsParentFolder() default "";

    String mappingsFolder() default "";

    Class<? extends Closure> recordIf() default Closure.class;

}
