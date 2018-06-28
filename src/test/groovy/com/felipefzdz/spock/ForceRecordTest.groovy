package com.felipefzdz.spock

import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.google.common.base.Charsets.UTF_8

@Stepwise
class ForceRecordTest extends Specification {

    @Rule
    public WireMockRule service1 = new WireMockRule(9080)

    @Rule
    public WireMockRule service2 = new WireMockRule(9081)

    @Shared
    @AutoCleanup
    CloseableHttpClient httpClient = HttpClientBuilder.create().build()

    def cleanupSpec() {
        new File('src/test/resources/wiremock').deleteDir()
    }

    @WiremockScenario(
            ports = [8081],
            targets = ['http://localhost:9080'],
            mappingsParentFolder = 'src/test/resources/wiremock/',
            mappingsFolder = 'forceRecord'
    )
    def "record"() {
        given:
        service1.stubFor(get(urlEqualTo("/some/thing"))
                .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello world")))
        when:
        String response = fetch('http://localhost:8081/some/thing')

        then:
        response == 'Hello world'
    }

    @WiremockScenario(
            ports = [8083],
            targets = ['http://localhost:9081'],
            mappingsParentFolder = 'src/test/resources/wiremock/',
            mappingsFolder = 'forceRecord',
            recordIf = { ForceRecordTest.forceRecord() }
    )
    def "force record"() {
        given:
        service2.stubFor(get(urlEqualTo("/some/thing"))
                .willReturn(aResponse()
                .withHeader("Content-Type", "text/plain")
                .withBody("Hello world 2")))
        when:
        String response = fetch('http://localhost:8083/some/thing')

        then:
        response == 'Hello world 2'
    }

    @WiremockScenario(
            replayPort = 8084,
            mappingsParentFolder = 'src/test/resources/wiremock/',
            mappingsFolder = 'forceRecord'
    )
    def "replay"() {
        expect:
        fetch('http://localhost:8084/some/thing') == 'Hello world 2'
    }

    private String fetch(String url) {
        httpClient.execute(new HttpGet(url)).withCloseable {
            it.entity == null ? null : EntityUtils.toString(it.entity, UTF_8.name())
        }
    }

    private static boolean forceRecord() {
        true
    }


}
