package com.bbh.itss.beadle.adapter

import com.bbh.itss.beadle.domain.change.ApprovalRole
import com.bbh.itss.beadle.domain.change.ApprovalState
import com.bbh.itss.beadle.domain.change.ChangeApproval
import spock.lang.Specification

import java.time.DayOfWeek

import static com.bbh.itss.beadle.domain.change.ApprovalRole.L1
import static com.bbh.itss.beadle.domain.change.ApprovalState.NOT_APPROVED
import static java.time.DayOfWeek.FRIDAY
import static org.apache.commons.lang3.StringUtils.trim

class RecordMapperSharedSpec extends Specification {

    def "a record is copied into another record by component name, with nested records, lists and maps converted"() {
        given:
        def source = new Team('Core', new Person(' Ada ', 36), [new Person('Bob', 41)], [(FRIDAY): new Person('Eve', 29)])

        when:
        def copy = RecordMapper.map(source, TeamView)

        then:
        copy == new TeamView('Core', new PersonView('Ada'), [new PersonView('Bob')], [(FRIDAY): new PersonView('Eve')])
        copy.rota() instanceof EnumMap
        RecordMapper.map(null, TeamView) == null
        RecordMapper.map('plain text', String) == 'plain text'
    }

    def "a record is built from the first source that has each component, as a method or as a field"() {
        expect:
        RecordMapper.map(PersonView, new Person('Ada', 36), new Holder('Grace')) == new PersonView('Ada')
        RecordMapper.map(PersonView, new Object(), new Holder('Grace')) == new PersonView('Grace')
        RecordMapper.map(PersonView, null, new Holder(null)) == new PersonView((String) null)
    }

    def "a record that mirrors another one passes through it, so the mirrored rules apply"() {
        expect:
        RecordMapper.map(new ApprovalRequest(L1, ' Ada ', null), ApprovalRequest) == new ApprovalRequest(L1, 'Ada', NOT_APPROVED)
    }

    def "a refusal of the target record is passed on unchanged"() {
        when:
        RecordMapper.map(new Person('Ada', -1), Person)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'age must not be negative'
    }

    record Person(String name, int age) {

        Person {
            name = trim(name)
            if (age < 0) {
                throw new IllegalArgumentException('age must not be negative')
            }
        }
    }

    record PersonView(String name) {
    }

    /** Mirrors a record of the main code: Groovy cannot bound a type parameter by a record of the same compilation. */
    record ApprovalRequest(ApprovalRole role, String approver, ApprovalState state) implements Mirrors<ChangeApproval> {
    }

    record Team(String name, Person lead, List<Person> members, Map<DayOfWeek, Person> rota) {
    }

    record TeamView(String name, PersonView lead, List<PersonView> members, Map<DayOfWeek, PersonView> rota) {
    }

    static class Holder {

        private final String name

        Holder(String name) {
            this.name = name
        }
    }
}
