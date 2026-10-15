package com.bbh.itss.beadle.config

import spock.lang.Specification

import static java.time.ZoneOffset.UTC

class ClockConfigurationSpec extends Specification {

    def "the application clock runs in UTC"() {
        expect:
        new ClockConfiguration().clock().zone == UTC
    }
}
