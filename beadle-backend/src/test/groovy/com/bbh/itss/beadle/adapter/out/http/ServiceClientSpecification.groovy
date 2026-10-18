package com.bbh.itss.beadle.adapter.out.http

import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.config.ServicesProperties
import com.bbh.itss.beadle.config.ServicesProperties.Endpoint
import com.bbh.itss.beadle.support.RecordingHttpServer
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter
import org.springframework.web.client.RestClient
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.time.Clock
import java.time.Instant

import static tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES

/**
 * Builds a client of Beadle-Internal or Beadle-ProTech, without Spring, against a {@link RecordingHttpServer} that
 * stands for the service; its JSON ignores unknown properties, as the JSON of the application does.
 */
abstract class ServiceClientSpecification extends Specification {

    static final String TOKEN = 'spec-service-token'
    static final JsonMapper JSON = JsonMapper.builder().disable(FAIL_ON_UNKNOWN_PROPERTIES).build()

    RecordingHttpServer service = new RecordingHttpServer()
    String signedInName = 'Mateusz Matan'
    Instant now = Instant.parse('2026-10-15T09:00:00Z')
    Clock clock = Stub { instant() >> { now } }

    def cleanup() {
        service.close()
    }

    RestClient.Builder builder() {
        RestClient.builder().configureMessageConverters { it.withJsonConverter(new JacksonJsonHttpMessageConverter(JSON)) }
    }

    CallerHeaders caller() {
        new CallerHeaders({ -> signedInName } as SignedInUserPort)
    }

    static ServicesProperties properties(String url, String token) {
        new ServicesProperties(token, new Endpoint(url), new Endpoint(url))
    }
}
