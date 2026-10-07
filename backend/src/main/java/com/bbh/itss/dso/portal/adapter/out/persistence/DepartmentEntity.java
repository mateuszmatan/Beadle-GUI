package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_DEPARTMENT")
@NoArgsConstructor(access = PROTECTED)
public class DepartmentEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Getter(PACKAGE)
    private String name;

    Department toDomain() {
        return RecordMapper.map(Department.class, this);
    }

    void rename(String name) {
        this.name = name;
    }
}
