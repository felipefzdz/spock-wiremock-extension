package com.felipefzdz.spock

import spock.lang.Specification

@WiremockScenario(
        resetRecordIf = { true },
        ports = [8081],
        targets = ['http://localhost:9080'],
        mappingsParentFolder = 'build/wiremock/'
)
class BaseSpec extends Specification {
}
