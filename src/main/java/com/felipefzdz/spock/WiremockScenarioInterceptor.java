package com.felipefzdz.spock;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.spockframework.runtime.extension.AbstractMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;
import spock.lang.Shared;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class WiremockScenarioInterceptor extends AbstractMethodInterceptor {

    private final Proxies proxies;
    private final WiremockScenarioMode mode;
    private final int replayPort;
    private static final Set<WiremockScenarioMode> alreadyIntercepted = new TreeSet<>();

    @Shared
    private List<WireMockServer> servers = new ArrayList<>();

    WiremockScenarioInterceptor(int[] ports, String[] targets, WiremockScenarioMode mode, int replayPort) {
        this.mode = mode;
        this.replayPort = replayPort;
        this.proxies = new Proxies(ports, targets, mode);
    }

    @Override
    public void interceptSetupSpecMethod(IMethodInvocation invocation) throws Throwable {
        if (alreadyIntercepted.add(mode)) {
            setupWiremockScenario();
        }
        invocation.proceed();
    }

    @Override
    public void interceptSetupMethod(IMethodInvocation invocation) throws Throwable {
        if (alreadyIntercepted.add(mode)) {
            setupWiremockScenario();
        }
        invocation.proceed();
    }

    @Override
    public void interceptCleanupSpecMethod(IMethodInvocation invocation) throws Throwable {
        invocation.proceed();
        cleanupWiremockScenario();
    }

    private void setupWiremockScenario() {
        switch (mode) {
            case RECORDING:
                record();
                break;
            case REPLAYING:
                replay();
                break;
            case DISABLED:
                break;
        }
    }

    private void record() {
        proxies.iterator().forEachRemaining(proxy -> {
            WireMockServer server = new WireMockServer(options().port(proxy.port));
            server.start();
            server.startRecording(recordSpec().forTarget(proxy.target));
            servers.add(server);
        });
    }

    private void replay() {
        WireMockServer server = new WireMockServer(options().port(replayPort).usingFilesUnderDirectory("src/test/resources"));
        server.start();
        servers.add(server);
    }

    private void cleanupWiremockScenario() {
        switch (mode) {
            case RECORDING: {
                servers.forEach(server -> {
                    server.stopRecording();
                    server.stop();
                });
                break;
            }
            case REPLAYING: {
                servers.forEach(WireMockServer::stop);
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
