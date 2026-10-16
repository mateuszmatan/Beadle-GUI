package com.bbh.itss.beadle

import com.bbh.itss.beadle.support.ArchitectureSpecification

class ArchitectureSpec extends ArchitectureSpecification {

    @Override
    Class<?> application() {
        BeadleApplication
    }

    @Override
    String[] httpAdapters() {
        ['com.bbh.itss.beadle.adapter.out.http..', 'com.bbh.itss.beadle.adapter.out.internal..',
         'com.bbh.itss.beadle.adapter.out.protech..']
    }
}
