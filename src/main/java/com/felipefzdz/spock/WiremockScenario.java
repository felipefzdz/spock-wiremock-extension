package com.felipefzdz.spock;

import groovy.lang.Closure;
import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(WiremockScenarioExtension.class)

public @interface WiremockScenario {
    int[] ports() default {};

    String[] targets() default {};

    int replayPort() default 8080;

    String mappingsParentFolder() default "";

    String mappingsFolder() default "";

    Class<? extends Closure> recordIf() default Closure.class;

}
