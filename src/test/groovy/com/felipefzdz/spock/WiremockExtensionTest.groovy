package com.felipefzdz.spock

import com.github.tomakehurst.wiremock.WireMockServer
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import spock.lang.*

import java.nio.file.Files
import java.nio.file.Paths

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.google.common.base.Charsets.UTF_8

@Stepwise
class WiremockExtensionTest extends Specification {

    @Shared
    @AutoCleanup
    CloseableHttpClient httpClient = HttpClientBuilder.create().build()

    def cleanupSpec() {
        new File('build/wiremock').deleteDir()
    }

    @WiremockScenario(
            ports = [8081],
            targets = ['http://localhost:8080'],
            mappingsFolder = 'build/wiremock/wiremockExtension'
    )
    def "record"() {
        given:
        WireMockServer server = new WireMockServer(8080)
        server.start()

        and:
        stubFor(get(urlEqualTo("/some/thing"))
                .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello world")))
        when:
        String response = fetch('http://localhost:8081/some/thing')

        then:
        response == 'Hello world'

        cleanup:
        server.stop()
    }

    @WiremockScenario(
            replayPort = 8082,
            mappingsFolder = 'build/wiremock/wiremockExtension'
    )
    def "replay"() {
        expect:
        fetch('http://localhost:8082/some/thing') == 'Hello world'
    }

    private String fetch(String url) {
        httpClient.execute(new HttpGet(url)).withCloseable {
            it.entity == null ? null : EntityUtils.toString(it.entity, UTF_8.name())
        }
    }


}
