package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "DSO_DEPARTMENT")
public class DepartmentEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    protected DepartmentEntity() {
    }

    String name() {
        return name;
    }

    Department toDomain() {
        return RecordMapper.map(Department.class, this);
    }

    void rename(String name) {
        this.name = name;
    }
}
