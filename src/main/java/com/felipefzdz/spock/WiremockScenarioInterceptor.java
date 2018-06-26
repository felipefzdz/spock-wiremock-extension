package com.felipefzdz.spock;

import com.github.tomakehurst.wiremock.WireMockServer;
import groovy.lang.Closure;
import org.spockframework.runtime.GroovyRuntimeUtil;
import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.ExtensionException;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import org.spockframework.runtime.extension.builtin.PreconditionContext;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class WiremockScenarioInterceptor extends AbstractMethodInterceptor {

    private final Proxies proxies;
    private final int replayPort;
    private final String mappingsParentFolder;
    private final String maybeMappingsFolder;
    private final Class<? extends Closure> recordIf;
    private final String featureName;
    private static final List<WireMockServer> recordingServers = new ArrayList<>();
    private static WireMockServer replayServer;
    private WiremockScenarioMode mode;

    WiremockScenarioInterceptor(
            int[] ports,
            String[] targets,
            int replayPort,
            String maybeMappingsParentFolder,
            String maybeMappingsFolder,
            Class<? extends Closure> recordIf,
            String featureName) {
        this.replayPort = replayPort;
        this.mappingsParentFolder = maybeMappingsParentFolder.isEmpty() ? "src/test/resources/wiremock/" : maybeMappingsParentFolder;
        this.maybeMappingsFolder = maybeMappingsFolder;
        this.recordIf = recordIf;
        this.featureName = featureName;
        this.proxies = new Proxies(ports, targets);
    }

    @Override
    public void interceptSetupSpecMethod(IMethodInvocation invocation) throws Throwable {
        String mappingsFolder = maybeMappingsFolder.isEmpty() ? mappingsFolderForSetupSpecMethod(invocation) : mappingsParentFolder + maybeMappingsFolder;
        mode = calculateMode(mappingsFolder);
        setupWiremockScenario(maybeMappingsFolder, mode);
        invocation.proceed();
    }

    private WiremockScenarioMode calculateMode(String mappingsFolder) {
        Optional<Closure> condition = createCondition();
        boolean isRecord = condition.map(this::evaluateCondition)
                .map(GroovyRuntimeUtil::isTruthy)
                .orElse(false);
        if (isRecord) {
            return WiremockScenarioMode.RESET_RECORDING;
        }
        return Files.exists(Paths.get(mappingsFolder)) ? WiremockScenarioMode.REPLAYING : WiremockScenarioMode.RECORDING;
    }

    private Optional<Closure> createCondition() {
        try {
            return Optional.of(recordIf.getConstructor(Object.class, Object.class).newInstance(null, null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Object evaluateCondition(Closure condition) {
        condition.setDelegate(new PreconditionContext());
        condition.setResolveStrategy(Closure.DELEGATE_ONLY);

        try {
            return condition.call();
        } catch (Exception e) {
            throw new ExtensionException("Failed to evaluate recordIf condition", e);
        }
    }

    @Override
    public void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
        if (invocation.getFeature().getName().equals(featureName)) {
            String mappingsFolder = maybeMappingsFolder.isEmpty() ? mappingsFolderForSetupMethod(invocation) : mappingsParentFolder + maybeMappingsFolder;
            mode = calculateMode(mappingsFolder);
            setupWiremockScenario(mappingsFolder, mode);
        }
        invocation.proceed();
    }

    private String mappingsFolderForSetupMethod(IMethodInvocation invocation) {
        String featureName = invocation.getFeature().getName();
        String specName = invocation.getSpec().getFilename().replace(".groovy", "");
        return mappingsParentFolder + featureName + specName;
    }

    private String mappingsFolderForSetupSpecMethod(IMethodInvocation invocation) {
        return mappingsParentFolder + invocation.getSpec().getFilename().replace(".groovy", "");
    }

    @Override
    public void interceptCleanupSpecMethod(IMethodInvocation invocation) throws Throwable {
        invocation.proceed();
        cleanupWiremockScenario();
    }

    @Override
    public void interceptCleanupMethod(IMethodInvocation invocation) throws Throwable {
        invocation.proceed();
        if (invocation.getFeature().getName().equals(featureName)) {
            cleanupWiremockScenario();
        }
    }

    private void setupWiremockScenario(String wiremockFolder, WiremockScenarioMode mode) {
        switch (mode) {
            case RESET_RECORDING:
                resetRecord(wiremockFolder);
                break;
            case RECORDING:
                record(wiremockFolder);
                break;
            case REPLAYING:
                replay(wiremockFolder);
                break;
        }
    }

    private void resetRecord(String wiremockFolder) {

        try {
            Path wiremockPath = Paths.get(wiremockFolder);
            if (Files.exists(wiremockPath)) {
                Files.walk(wiremockPath)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
            record(wiremockFolder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void record(String wiremockFolder) {
        proxies.iterator().forEachRemaining(proxy -> {
            createDirectory(wiremockFolder);
            WireMockServer server = new WireMockServer(options().port(proxy.port).usingFilesUnderDirectory(wiremockFolder));
            server.start();
            server.startRecording(recordSpec().forTarget(proxy.target));
            recordingServers.add(server);
        });
    }

    private Path createDirectory(String wiremockFolder) {
        try {
            return Files.createDirectories(Paths.get(wiremockFolder + "/mappings"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void replay(String wiremockFolder) {
        replayServer = new WireMockServer(options().port(replayPort).usingFilesUnderDirectory(wiremockFolder));
        replayServer.start();
    }

    private void cleanupWiremockScenario() {
        switch (mode) {
            case RECORDING: {
                cleanupRecording();
                break;
            }
            case RESET_RECORDING: {
                cleanupRecording();
                break;
            }
            case REPLAYING: {
                replayServer.stop();
                break;
            }
        }
    }

    private void cleanupRecording() {
        recordingServers.forEach(server -> {
            server.stopRecording();
            server.stop();
        });
        recordingServers.clear();
    }

    void install(List<List<IMethodInterceptor>> interceptors) {
        interceptors.forEach(i -> i.add(this));
    }

    private static class Proxies {
        private final List<Proxy> proxies = new ArrayList<>();

        private Proxies(int[] ports, String[] targets) {
            assert ports.length == targets.length;
            for (int i = 0; i < ports.length; i++) {
                proxies.add(new Proxy(ports[i], targets[i]));
            }
        }

        Iterator<Proxy> iterator() {
            return proxies.iterator();
        }
    }

    private static class Proxy {
        private final int port;
        private final String target;

        private Proxy(int port, String target) {
            this.port = port;
            this.target = target;
        }
    }
}
