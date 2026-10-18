package com.bbh.itss.beadle.support

import java.time.LocalDate

import static com.bbh.itss.beadle.support.RecordingHttpServer.Answer.of as reply

/**
 * Beadle-Internal and Beadle-ProTech at the level of docs/CONTRACTS.md, as far as Beadle-GUI calls them: one local
 * HTTP server per test JVM ({@link #SHARED}) answers {@code /api/internal} with a Jira of two FixVersions per project,
 * two epics and two stories per FixVersion, and a CyberTrack that numbers its tickets; and {@code /api/protech} with a
 * ProTech that keeps the changes raised on it, numbers their tasks, applies an update at once and reminds the people
 * it names. A spec moves a change in ProTech through {@link #proTechChange}. The Spring specs reach it through
 * {@code beadle.services.internal.url} and {@code beadle.services.protech.url} set to {@code ${beadle.stand-in.url}},
 * the system property {@link #SHARED} sets.
 */
class StandInServices extends RecordingHttpServer {

    static final String URL_PROPERTY = 'beadle.stand-in.url'
    static final StandInServices SHARED = new StandInServices()

    static final List<String> RELEASE_APPROVERS = ['Rebecca Lawson', 'Thomas Ashby']
    static final List<String> TASK_APPROVERS = ['Jane Smith']
    static final Map<String, List<Map>> LOOKUPS = [
            'users'              : ['Mateusz Matan', 'Jane Smith', 'Olivia Bennett', 'James Carter', 'Rebecca Lawson',
                                    'Thomas Ashby', 'Emma Brooks', 'Ann Lee', 'Grace Turner'].collect {
                [value: it, detail: it.toLowerCase(Locale.ROOT).replace(' ', '.') + '@bbh.com']
            },
            'business-services'        : [[value: 'Corporate Technology', detail: 'Cost centre 4310'],
                                    [value: 'Custody', detail: 'Cost centre 4410'],
                                    [value: 'Fund Services', detail: 'Cost centre 4510']],
            'assignment-groups'  : [[value: 'Release Management', detail: 'Corporate Technology'],
                                    [value: 'Service Desk', detail: 'Corporate Technology'],
                                    [value: 'Database Administration', detail: 'Infrastructure & Operations']],
            'releases'           : [[value: 'CertScanner 4.2', detail: 'CERT'], [value: 'PayHub 2.1', detail: 'PAYHUB']],
            'configuration-items': [[value: 'CertScanner', detail: 'Certificate Management'],
                                    [value: 'PayHub', detail: 'Payments']],
            'incidents'          : [[value: 'INC0104211', detail: 'Login page times out for fund accountants']],
            'problems'           : [[value: 'PRB0040112', detail: 'Connection pool exhausted at month end']],
            'clients'            : [[value: 'Alder Ridge Pension Fund', detail: 'Fund administration'],
                                    [value: 'Bluewater Insurance Group', detail: 'Custody']]]

    private final Map<String, Map> changes = [:]
    private int numbers = 31_000
    private int tickets = 100

    private StandInServices() {
        System.setProperty(URL_PROPERTY, url)
        internal()
        proTech()
    }

    /** The change {@code number} as ProTech holds it, to be moved the way ProTech would move it. */
    synchronized Map proTechChange(String number) {
        changes[number]
    }

    private void internal() {
        route('GET', '/api/internal/status') { request, groups ->
            reply(200, [jira: [connected: true, mode: 'live'], cyberTrack: [connected: true, mode: 'live']])
        }
        route('GET', '/api/internal/jira/projects/([^/]+)/versions') { request, groups ->
            reply(200, versions(groups[0]))
        }
        route('GET', '/api/internal/jira/projects/([^/]+)/epics') { request, groups ->
            reply(200, issues(groups[0], request.params.fixVersion).findAll { it.epicKey == null })
        }
        route('GET', '/api/internal/jira/projects/([^/]+)/stories') { request, groups ->
            List<String> epics = request.params.epicKeys?.tokenize(',') ?: []
            reply(200, issues(groups[0], request.params.fixVersion).findAll { it.epicKey in epics })
        }
        route('POST', '/api/internal/cybertrack/tickets') { request, groups ->
            String key = "SCP-${ticket()}"
            reply(201, [key: key, url: "https://cybertrack.bbh.com/browse/$key".toString()])
        }
    }

    private void proTech() {
        route('GET', '/api/protech/status') { request, groups -> reply(200, [connected: true, mode: 'live']) }
        route('POST', '/api/protech/changes') { request, groups -> reply(201, raised(request.json as Map)) }
        route('POST', '/api/protech/changes/read') { request, groups ->
            reply(200, read((request.json as List<Map>)*.number))
        }
        route('POST', '/api/protech/changes/([^/]+)/tasks') { request, groups ->
            String number = task(groups[0], request.json as Map)
            number ? reply(201, [number: number]) : notFound(groups[0])
        }
        route('PUT', '/api/protech/changes/([^/]+)') { request, groups ->
            updated(groups[0], request.json as Map) ? reply(204) : notFound(groups[0])
        }
        route('POST', '/api/protech/changes/([^/]+)/reminders') { request, groups ->
            List<String> people = reminded(groups[0], request.json as Map)
            people == null ? notFound(groups[0]) : reply(200, people)
        }
        route('GET', '/api/protech/lookups/([^/]+)') { request, groups ->
            List<Map> lookups = LOOKUPS[groups[0]]
            String query = request.params.q?.toLowerCase(Locale.ROOT)
            lookups == null ? notFound(groups[0]) : reply(200, lookups.findAll { lookup ->
                !query || lookup.values().any { it.toLowerCase(Locale.ROOT).contains(query) }
            }.take((request.params.limit ?: '20') as int))
        }
    }

    private static List<Map> versions(String project) {
        LocalDate today = LocalDate.now()
        [[name: "$project 2.1".toString(), released: false, releaseDate: today.plusDays(14).toString()],
         [name: "$project 2.0".toString(), released: true, releaseDate: today.minusDays(14).toString()]]
    }

    /** Per FixVersion two epics and a story of each, keyed {@code CERT-11} to {@code CERT-14} for the first version. */
    private static List<Map> issues(String project, String fixVersion) {
        int index = versions(project).findIndexOf { it.name.equalsIgnoreCase(fixVersion?.trim()) }
        if (index < 0) {
            return []
        }
        String updated = LocalDate.now().minusDays(1).toString()
        Closure<Map> issue = { int number, String summary, Integer epic ->
            [key: "$project-${10 * (index + 1) + number}".toString(), summary: summary, status: 'In Progress',
             epicKey: epic == null ? null : "$project-${10 * (index + 1) + epic}".toString(), updated: updated]
        }
        [issue(1, 'Single sign-on with BBH SSO', null), issue(2, 'Audit trail of user actions', null),
         issue(3, 'Map SSO groups to Beadle roles', 1), issue(4, 'Show the audit trail on the details page', 2)]
    }

    private synchronized int ticket() {
        ++tickets
    }

    private synchronized Map raised(Map draft) {
        String number = 'CHG' + String.format('%07d', ++numbers)
        String link = "https://protech.bbh.com/$number".toString()
        changes[number] = draft + [number: number, url: link]
        [number: number, url: link]
    }

    private synchronized Map read(List<String> numbers) {
        numbers.findAll { changes.containsKey(it) }.collectEntries { [it, changes[it]] }
    }

    private synchronized String task(String change, Map task) {
        Map stored = changes[change]
        if (stored == null) {
            return null
        }
        Map created = numbered(task)
        stored.tasks = (stored.tasks ?: []) + [created]
        created.number
    }

    /** Takes the update at once; a task it does not list any longer is canceled, a new one numbered. */
    private synchronized boolean updated(String number, Map change) {
        Map stored = changes[number]
        if (stored == null) {
            return false
        }
        Map<String, Map> known = (stored.tasks ?: []).collectEntries { [it.number, it] }
        List<Map> tasks = (change.tasks as List<Map>).collect { Map task ->
            known[task.number] ? known[task.number] + [details: task.details, start: task.start] : numbered(task)
        }
        List<String> listed = tasks*.number
        tasks += known.values().findAll { !(it.number in listed) }.collect { it + [state: 'CANCELED'] }
        stored.putAll(change.subMap('shortDescription', 'description', 'schedule', 'template') + [tasks: tasks])
        true
    }

    private synchronized List<String> reminded(String number, Map approval) {
        Map stored = changes[number]
        if (stored == null) {
            return null
        }
        approval.task ? (stored.tasks.find { it.number == approval.task }?.approvers ?: [])
                : [stored.approvals.find { it.role == approval.role }?.approver].findAll()
    }

    private Map numbered(Map task) {
        task + [number: 'CTASK' + String.format('%07d', ++numbers), approval: 'NOT_APPROVED', state: 'OPEN',
                reminder: null,
                approvers: task.details?.assignmentGroup == 'Release Management' ? RELEASE_APPROVERS : TASK_APPROVERS]
    }

    private static RecordingHttpServer.Answer notFound(String what) {
        reply(404, [title: 'Not Found', status: 404, detail: "ProTech has no $what".toString()],
                'application/problem+json')
    }
}
