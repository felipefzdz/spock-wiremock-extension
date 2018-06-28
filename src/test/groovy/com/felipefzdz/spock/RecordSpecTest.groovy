package com.felipefzdz.spock

import com.github.tomakehurst.wiremock.WireMockServer
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.google.common.base.Charsets.UTF_8

@WiremockScenario(
        recordIf = { true },
        ports = [8081],
        targets = ['http://localhost:9080'],
        mappingsParentFolder = 'build/wiremock/'
)
class RecordSpecTest extends Specification {

    @Shared
    @AutoCleanup
    CloseableHttpClient httpClient = HttpClientBuilder.create().build()

    def "record spec"() {
        given:
        WireMockServer server = new WireMockServer(9080)
        server.start()

        and:
        configureFor("localhost", 9080)
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

    private String fetch(String url) {
        httpClient.execute(new HttpGet(url)).withCloseable {
            it.entity == null ? null : EntityUtils.toString(it.entity, UTF_8.name())
        }
    }


}
