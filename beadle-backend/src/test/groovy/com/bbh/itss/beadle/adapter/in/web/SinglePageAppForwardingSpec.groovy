package com.bbh.itss.beadle.adapter.in.web

import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.web.context.support.GenericWebApplicationContext
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup

class SinglePageAppForwardingSpec extends Specification {

    @Shared
    MockMvc mvc = mvc()

    def "the app's address #path is served by the Angular index page, an unknown one for the app's wildcard route"() {
        when:
        def response = mvc.perform(get(path)).andReturn().response

        then:
        response.forwardedUrl == '/index.html'

        where:
        path << ['/changes', '/changes/7', '/admin/products/12/edit', '/new-change/product', '/no-such-page',
                 '/a/b/c', '/pipelines.old/7', '/apis']
    }

    def "#path is left to the API, the actuator, the sign-in and the static resources"() {
        when:
        def response = mvc.perform(get(path)).andReturn().response

        then:
        response.forwardedUrl == null
        response.status == 404

        where:
        path << ['/api', '/api/unknown', '/actuator/unknown', '/error', '/oauth2/unknown', '/login/oauth2/code/x',
                 '/favicon.ico', '/index.htm', '/assets/logo.svg', '/no-such-page/main.js', '/']
    }

    def "only a GET or a HEAD of an address is the app's"() {
        expect:
        SinglePageAppForwarding.isAppAddress(method, '/no-such-page') == expected

        where:
        method   || expected
        'GET'    || true
        'HEAD'   || true
        'POST'   || false
        'PUT'    || false
        'DELETE' || false
    }

    private static MockMvc mvc() {
        def context = new GenericWebApplicationContext(new MockServletContext())
        context.registerBean('conversionService', DefaultConversionService)
        new AnnotatedBeanDefinitionReader(context).register(DelegatingWebMvcConfiguration, SinglePageAppForwarding)
        context.refresh()
        webAppContextSetup(context).build()
    }
}
