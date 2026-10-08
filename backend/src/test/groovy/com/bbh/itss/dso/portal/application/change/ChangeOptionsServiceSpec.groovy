package com.bbh.itss.dso.portal.application.change

import com.bbh.itss.dso.portal.application.change.port.in.ChangeOptions.TypeOption
import spock.lang.Specification

class ChangeOptionsServiceSpec extends Specification {

    def "the options are the categories, the change types with their labels and the risk answers in form order"() {
        when:
        def options = new ChangeOptionsService().options()

        then:
        options.categories() == ['Application', 'Hardware', 'Infrastructure', 'System Software', 'Network', 'Telecom',
                                 'Data Amendment', 'Desktop Software', 'Storage', 'Facilities', 'Other', 'Database']
        options.types() == [new TypeOption('STANDARD', 'Standard'), new TypeOption('EMERGENCY', 'Emergency'),
                            new TypeOption('BUSINESS_CRITICAL', 'Business Critical'), new TypeOption('MODEL', 'Model')]
        options.risk().keySet().toList() == ['bbhWorkgroups', 'changeComplexity', 'bbhUsers', 'validationComplexity',
                                             'bbhApplications', 'backoutTesting', 'clientsOutsideBbh',
                                             'platformStatus', 'businessImpact']
        options.risk().bbhUsers == ['Less than 5', '5-25', '26-250', 'All users']
    }
}
