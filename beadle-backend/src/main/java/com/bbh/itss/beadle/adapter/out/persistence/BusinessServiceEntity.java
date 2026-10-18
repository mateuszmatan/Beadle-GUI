package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.domain.catalog.BusinessService;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.bbh.itss.beadle.adapter.RecordMapper.map;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_BUSINESS_SERVICE")
@NoArgsConstructor(access = PROTECTED)
public class BusinessServiceEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Getter(PACKAGE)
    private String name;

    BusinessService toDomain() {
        return map(BusinessService.class, this);
    }

    void rename(String name) {
        this.name = name;
    }
}
