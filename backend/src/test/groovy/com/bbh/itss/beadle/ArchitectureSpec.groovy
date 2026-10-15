package com.bbh.itss.beadle

import com.bbh.itss.beadle.support.ArchitectureSpecification

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        BeadleApplication
    }

    @Override
    String httpAdapter() {
        'com.bbh.itss.beadle.adapter.out.jira..'
    }
}
