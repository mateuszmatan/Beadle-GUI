package com.bbh.itss.beadle.adapter

import com.bbh.itss.beadle.domain.audit.ActionRecord
import spock.lang.Specification
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

import static java.nio.charset.StandardCharsets.UTF_8

class AuditDetailsSpec extends Specification {

    static final int MAX = ActionRecord.DETAILS_MAX
    static final String RESULT = '{"reminded":[{"role":"SUPPORT","people":["Jane Smith"]}],"created":["CTASK0041003"]}'

    JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()

    def "details that fit are written as they are"() {
        given:
        def details = tree('{"id":7,"command":{"name":"CertScanner","tags":["a","b"],"owner":null,"risk":1.5}}')

        expect:
        AuditDetails.write(json, details) == '{"id":7,"command":{"name":"CertScanner","tags":["a","b"],' +
                '"owner":null,"risk":1.5}}'
        AuditDetails.write(json, null) == null
    }

    def "a template of 12 long tasks is written as JSON within 32 KB, the longest strings cut to one length"() {
        given:
        def tasks = (1..12).collect { [assignmentGroup: 'Technology Architecture', shortDescription: 'Task ' + it,
                                       description: 'Step ' + it + ' ' + 'x' * 3_890, additionalComments: 'Short'] }
        def details = tree(json.writeValueAsString([productId: 1, version: 3, template: [category: 'Application'],
                                                    tasks: tasks]))
        details.set(ActionRecord.RESULT, tree(RESULT))

        when:
        def written = AuditDetails.write(json, details)
        def read = json.readTree(written)

        then:
        bytes(written) <= MAX
        bytes(written) > MAX - 12 * 2
        read.get('productId').asInt() == 1
        read.get('version').asInt() == 3
        read.get('template') == tree('{"category":"Application"}')
        read.get('tasks').size() == 12
        def descriptions = read.get('tasks').collect { it.get('description').stringValue() }
        descriptions.every { it.endsWith(AuditDetails.CUT) }
        descriptions*.length().unique().size() == 1
        descriptions.withIndex().every { text, index -> text.startsWith("Step ${index + 1} xxx") }
        read.get('tasks').every { it.get('shortDescription').stringValue().startsWith('Task ') }
        read.get('tasks').every { it.get('additionalComments').stringValue() == 'Short' }
        read.get(ActionRecord.RESULT) == tree(RESULT)
        written.endsWith(',"result":' + RESULT + '}')
    }

    def "a string of #what is cut on whole characters and stays JSON"() {
        given:
        def details = json.createObjectNode()
        details.putObject('request').put('description', text)

        when:
        def written = AuditDetails.write(json, details)
        def description = json.readTree(written).get('request').get('description').stringValue()

        then:
        bytes(written) <= MAX
        bytes(written) > MAX - 8
        description.endsWith(AuditDetails.CUT)
        text.startsWith(description - AuditDetails.CUT)
        !Character.isHighSurrogate(description.charAt(description.length() - 2))

        where:
        what                                  | text
        'characters of two bytes'             | 'é' * 40_000
        'characters of four bytes'            | '😀' * 20_000
        'characters of four bytes after one'  | 'a' + '😀' * 20_000
        'quotes, backslashes and line breaks' | '"\\\n' * 30_000
    }

    def "arrays keep their first elements and say how many they left out when cutting the strings is not enough"() {
        given:
        def details = tree(json.writeValueAsString([id: 7, request: [storyKeys: (1..20_000).collect { 'CERT-' + it }]]))

        when:
        def written = AuditDetails.write(json, details)
        def keys = json.readTree(written).get('request').get('storyKeys').collect { it.stringValue() }

        then:
        bytes(written) <= MAX
        json.readTree(written).get('id').asInt() == 7
        keys.size() > 1_000
        keys.init() == (1..keys.size() - 1).collect { 'CERT-' + it }
        keys.last() == AuditDetails.leftOut(20_000 - keys.size() + 1)
        keys.last() ==~ /… \d+ more/
    }

    def "details too long even trimmed give way to their length, the top-level values that still fit and the result"() {
        given:
        def details = json.createObjectNode().put('id', 7)
        def request = details.putObject('request')
        (1..10_000).each { request.put("field$it", it) }
        details.put('businessServiceId', 3)
        details.set(ActionRecord.RESULT, tree(RESULT))
        def whole = json.writeValueAsBytes(details).length

        expect:
        AuditDetails.write(json, details) ==
                '{"truncated":true,"bytes":' + whole + ',"id":7,"businessServiceId":3,"result":' + RESULT + '}'
    }

    def "a string is cut to its length and marked, and kept when it is no longer"() {
        expect:
        AuditDetails.cut('CertScanner', 4) == 'Cert…'
        AuditDetails.cut('CertScanner', 11) == 'CertScanner'
        AuditDetails.cut('a😀b', 2) == 'a…'
        AuditDetails.cut('a😀b', 3) == 'a😀…'
    }

    def "whatever the tree and the limit, the details are JSON within the limit, and unchanged when they fit"() {
        given:
        def random = new Random(20261015)
        Map<String, Integer> outcomes = [:].withDefault { 0 }

        when:
        300.times {
            ObjectNode details = json.createObjectNode()
            (0..random.nextInt(4)).each { details.set("argument$it", randomNode(random, 0)) }
            if (random.nextBoolean()) {
                details.set(ActionRecord.RESULT, tree(RESULT))
            }
            int max = 120 + random.nextInt(6_000)
            String whole = new String(json.writeValueAsBytes(details), UTF_8)
            String written = AuditDetails.write(json, details, max)
            assert bytes(written) <= max: written
            assert json.readTree(written) != null
            assert bytes(whole) > max || written == whole
            outcomes[written == whole ? 'whole' : written.startsWith('{"truncated":true,') ? 'truncated'
                    : written.contains(' more"') ? 'arrays cut' : 'strings cut']++
        }

        then:
        outcomes.keySet() == ['whole', 'strings cut', 'arrays cut', 'truncated'] as Set
        outcomes.values().every { it >= 5 }
    }

    private JsonNode randomNode(Random random, int depth) {
        switch (random.nextInt(depth < 3 ? 6 : 4)) {
            case 0: return json.valueToTree(randomText(random))
            case 1: return json.valueToTree(random.nextInt())
            case 2: return json.valueToTree(random.nextBoolean() ? null : random.nextDouble())
            case 3: return json.valueToTree(random.nextBoolean())
            case 4:
                def array = json.createArrayNode()
                if (random.nextInt(4) == 0) {
                    random.nextInt(1_000).times { array.add(randomNode(random, 3)) }
                } else {
                    random.nextInt(4).times { array.add(randomNode(random, depth + 1)) }
                }
                return array
            default:
                def object = json.createObjectNode()
                random.nextInt(5).times { object.set(randomText(random).take(12) + it, randomNode(random, depth + 1)) }
                return object
        }
    }

    private static String randomText(Random random) {
        def alphabet = ['a', 'Z', ' ', '"', '\\', '\n', '\u0001', 'é', '€', '😀', '…']
        int length = random.nextInt(4) == 0 ? random.nextInt(4_000) : random.nextInt(40)
        (0..<length).collect { alphabet[random.nextInt(alphabet.size())] }.join()
    }

    private ObjectNode tree(String text) {
        json.readTree(text) as ObjectNode
    }

    private static int bytes(String text) {
        text.getBytes(UTF_8).length
    }
}
