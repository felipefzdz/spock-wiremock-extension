package com.felipefzdz.spock;

import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

import static java.util.Arrays.asList;

public class WiremockScenarioExtension extends AbstractAnnotationDrivenExtension<WiremockScenario> {

    @Override
    public void visitSpecAnnotation(WiremockScenario annotation, SpecInfo spec) {
        WiremockScenarioInterceptor interceptor = new WiremockScenarioInterceptor(
                annotation.ports(),
                annotation.targets(),
                annotation.replayPort(),
                annotation.mappingsParentFolder(),
                annotation.mappingsFolder(),
                annotation.resetRecordIf(),
                ""
        );
        interceptor.install(asList(spec.getSetupSpecInterceptors(), spec.getCleanupSpecInterceptors()));
    }

    @Override
    public void visitFeatureAnnotation(WiremockScenario annotation, FeatureInfo feature) {
        WiremockScenarioInterceptor interceptor = new WiremockScenarioInterceptor(
                annotation.ports(),
                annotation.targets(),
                annotation.replayPort(),
                annotation.mappingsParentFolder(),
                annotation.mappingsFolder(),
                annotation.resetRecordIf(),
                feature.getName()
        );
        interceptor.install(asList(feature.getSpec().getSetupInterceptors(), feature.getSpec().getCleanupInterceptors()));
    }
}
