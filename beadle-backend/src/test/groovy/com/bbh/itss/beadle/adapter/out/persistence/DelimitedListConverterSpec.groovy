package com.bbh.itss.beadle.adapter.out.persistence

import spock.lang.Specification

class DelimitedListConverterSpec extends Specification {

    def converter = new DelimitedListConverter()

    def "#values are stored as #column"() {
        expect:
        converter.convertToDatabaseColumn(values) == column

        where:
        values                                           || column
        ['**/*.jar', ' **/*.war ', '', null, '**/*.jar'] || '**/*.jar\n**/*.war'
        ['src/**/*.java,src/**/*.kt', 'docs']            || 'src/**/*.java,src/**/*.kt\ndocs'
        ['', '  ']                                       || null
        []                                               || null
        null                                             || null
    }

    def "#column is read as #values"() {
        expect:
        converter.convertToEntityAttribute(column) == values

        where:
        column                                || values
        '**/*.jar\n **/*.war\n\n**/*.jar'     || ['**/*.jar', '**/*.war']
        'src/**/*.java,src/**/*.kt\ndocs'     || ['src/**/*.java,src/**/*.kt', 'docs']
        ' \n '                                || []
        ''                                    || []
        null                                  || []
    }
}
