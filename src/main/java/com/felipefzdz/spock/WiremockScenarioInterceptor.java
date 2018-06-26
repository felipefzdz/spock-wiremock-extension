package com.felipefzdz.spock;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;

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
    private final WiremockScenarioMode mode;
    private final int replayPort;
    private final String mappingsFolder;
    private static final Set<WiremockScenarioMode> alreadyIntercepted = new TreeSet<>();
    private static final List<WireMockServer> recordingServers = new ArrayList<>();
    private static WireMockServer replayServer;

    WiremockScenarioInterceptor(
            int[] ports,
            String[] targets,
            WiremockScenarioMode mode,
            int replayPort,
            String mappingsFolder) {
        this.mode = mode;
        this.replayPort = replayPort;
        this.mappingsFolder = mappingsFolder;
        this.proxies = new Proxies(ports, targets, mode);
    }

    @Override
    public void interceptSetupSpecMethod(IMethodInvocation invocation) throws Throwable {
        if (alreadyIntercepted.add(mode)) {
            if (mappingsFolder.isEmpty()) {
                String specName = invocation.getSpec().getFilename().replace(".groovy", "");
                setupWiremockScenario(specName);
            } else {
                setupWiremockScenario(mappingsFolder);
            }
        }
        invocation.proceed();
    }

    @Override
    public void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
        if (alreadyIntercepted.add(mode)) {
            if (mappingsFolder.isEmpty()) {
                String featureName = invocation.getFeature().getName();
                String specName = invocation.getSpec().getFilename().replace(".groovy", "");
                setupWiremockScenario(featureName + specName);
            } else {
                setupWiremockScenario(mappingsFolder);
            }
        }
        invocation.proceed();
    }

    @Override
    public void interceptCleanupSpecMethod(IMethodInvocation invocation) throws Throwable {
        invocation.proceed();
        if (alreadyIntercepted.remove(mode)) {
            cleanupWiremockScenario();
        }
    }

    @Override
    public void interceptCleanupMethod(IMethodInvocation invocation) throws Throwable {
        invocation.proceed();
        if (alreadyIntercepted.remove(mode)) {
            cleanupWiremockScenario();
        }
    }

    private void setupWiremockScenario(String wiremockFolder) {
        switch (mode) {
            case RECORDING:
                record(wiremockFolder);
                break;
            case REPLAYING:
                replay(wiremockFolder);
                break;
            case DISABLED:
                break;
        }
    }

    private void record(String wiremockFolder) {
        proxies.iterator().forEachRemaining(proxy -> {
            createDirectory(wiremockFolder);
            WireMockServer server = new WireMockServer(options().port(proxy.port).usingFilesUnderDirectory("build/wiremock/" + wiremockFolder));
            server.start();
            server.startRecording(recordSpec().forTarget(proxy.target));
            recordingServers.add(server);
        });
    }

    private Path createDirectory(String wiremockFolder) {
        try {
            return Files.createDirectories(Paths.get("build/wiremock/" + wiremockFolder + "/mappings"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void replay(String wiremockFolder) {
        replayServer = new WireMockServer(options().port(replayPort).usingFilesUnderDirectory("build/wiremock/" + wiremockFolder));
        replayServer.start();
    }

    private void cleanupWiremockScenario() {
        switch (mode) {
            case RECORDING: {
                recordingServers.forEach(server -> {
                    server.stopRecording();
                    server.stop();
                });
                recordingServers.clear();
                break;
            }
            case REPLAYING: {
                replayServer.stop();
                break;
            }
            case DISABLED:
                break;
        }
    }

    void install(List<List<IMethodInterceptor>> interceptors) {
        interceptors.forEach(i -> i.add(this));
    }

    private static class Proxies {
        private final List<Proxy> proxies = new ArrayList<>();

        private Proxies(int[] ports, String[] targets, WiremockScenarioMode mode) {
            boolean recording = mode.equals(WiremockScenarioMode.RECORDING);
            if (recording) {
                assert ports.length == targets.length;
                for (int i = 0; i < ports.length; i++) {
                    proxies.add(new Proxy(ports[i], targets[i]));
                }
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
