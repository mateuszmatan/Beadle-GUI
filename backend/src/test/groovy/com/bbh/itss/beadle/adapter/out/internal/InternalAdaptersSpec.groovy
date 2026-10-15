package com.bbh.itss.beadle.adapter.out.internal

import com.bbh.itss.beadle.adapter.out.http.ServiceClientSpecification
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort
import com.bbh.itss.beadle.application.change.port.out.JiraPort
import com.bbh.itss.beadle.domain.change.JiraIssue
import com.bbh.itss.beadle.domain.change.JiraVersion
import com.bbh.itss.beadle.domain.change.SecureCodingTicket

import java.time.LocalDate

import static com.bbh.itss.beadle.support.ChangeFixtures.secureCoding
import static java.time.Duration.ofSeconds

class InternalAdaptersSpec extends ServiceClientSpecification {

    static final SecureCodingTicket TICKET = new SecureCodingTicket('CHG0031001', 'CertScanner', '10202026',
            secureCoding())
    static final Map DEMO = [connected: false, mode: 'demo']
    static final Map LIVE = [connected: true, mode: 'live']

    JiraPort getJira() {
        bean(JiraPort)
    }

    CyberTrackPort getCyberTrack() {
        bean(CyberTrackPort)
    }

    def "the versions, epics and stories of a Jira project come from Beadle-Internal"() {
        given:
        service.answer('GET', '/api/internal/jira/projects/CERT/versions', 200,
                [[name: 'CERT 4.2', released: false, releaseDate: '2026-11-01'],
                 [name: 'CERT 4.1', released: true, releaseDate: null]])
        service.answer('GET', '/api/internal/jira/projects/CERT/epics', 200,
                [[key: 'CERT-12', summary: 'Expiry alerts', status: 'In Progress', epicKey: null, updated: '2026-10-01']])
        service.answer('GET', '/api/internal/jira/projects/CERT/stories', 200,
                [[key: 'CERT-14', summary: 'Alert by e-mail', status: 'Done', epicKey: 'CERT-12', updated: '2026-10-02']])

        when:
        def versions = jira.versions('CERT')
        def epics = jira.epics('CERT', 'CERT 4.2+hotfix')
        def stories = jira.stories('CERT', 'CERT 4.2', ['CERT-12', 'CERT-13'])

        then:
        versions == [new JiraVersion('CERT 4.2', false, LocalDate.parse('2026-11-01')),
                     new JiraVersion('CERT 4.1', true, null)]
        epics == [new JiraIssue('CERT-12', 'Expiry alerts', 'In Progress', null, LocalDate.parse('2026-10-01'))]
        stories*.epicKey() == ['CERT-12']
        service.requests*.decodedQuery == [null, 'fixVersion=CERT 4.2+hotfix', 'fixVersion=CERT 4.2&epicKeys=CERT-12,CERT-13']
        service.requests.every { it.header('Authorization') == "Bearer $TOKEN" && it.header('X-Beadle-User') == USER }
    }

    def "stories of no epic are not asked for"() {
        expect:
        jira.stories('CERT', 'CERT 4.2', []) == []
        service.requests == []
    }

    def "a secure coding ticket is created in CyberTrack through Beadle-Internal with an ISO implementation date"() {
        given:
        service.answer('POST', '/api/internal/cybertrack/tickets', 201,
                [key: 'SCP-123', url: 'https://cybertrack.bbh.com/browse/SCP-123'])

        when:
        def key = cyberTrack.create(TICKET)

        then:
        key == 'SCP-123'
        service.last.json == [changeNumber    : 'CHG0031001', applicationName: 'CertScanner',
                              implementationDate: '2026-10-20',
                              inputs          : [apoNumber   : 'APO-12345',
                                                 bitbucketUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                                                 artifactLink: 'https://jenkins.bbh.com/job/CERT/job/cert-release/',
                                                 qcApplicationLink: 'https://cert.qc.bbh.com']]
    }

    def "an answer without the key of the ticket fails the call"() {
        given:
        service.answer('POST', '/api/internal/cybertrack/tickets', 201, [url: null])

        when:
        cyberTrack.create(TICKET)

        then:
        def failed = thrown(UncheckedIOException)
        failed.message == 'Beadle-Internal answered without the key of the new secure coding ticket'
    }

    def "Jira and CyberTrack are connected as Beadle-Internal reports them, from one status asked at most every 30 s"() {
        given:
        service.answer('GET', '/api/internal/status', 200, [jira: LIVE, cyberTrack: DEMO, confluence: DEMO, qtest: DEMO])

        expect:
        jira.connected()
        !cyberTrack.connected()
        service.requests.size() == 1

        when:
        service.answer('GET', '/api/internal/status', 200, [jira: DEMO, cyberTrack: LIVE, confluence: DEMO, qtest: DEMO])
        clock.advance(ofSeconds(30))

        then:
        !jira.connected()
        cyberTrack.connected()
        service.requests.size() == 2
    }

    def "an unreachable or failing Beadle-Internal connects neither"() {
        given:
        service.answer('GET', '/api/internal/status', 503, '')

        expect:
        !jira.connected()
        !cyberTrack.connected()

        when:
        context.close()
        context = start('http://127.0.0.1:1')
        jira.versions('CERT')

        then:
        !jira.connected()
        def failed = thrown(UncheckedIOException)
        failed.message.startsWith('Beadle-Internal did not answer when asked to read the versions of Jira project CERT: ')
    }
}
