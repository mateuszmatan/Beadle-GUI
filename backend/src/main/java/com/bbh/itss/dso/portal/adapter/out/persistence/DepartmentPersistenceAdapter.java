package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class DepartmentPersistenceAdapter implements DepartmentRepositoryPort {

    private final DepartmentJpaRepository departments;

    DepartmentPersistenceAdapter(DepartmentJpaRepository departments) {
        this.departments = departments;
    }

    @Override
    public List<Department> findAll() {
        return departments.findAll().stream().map(DepartmentEntity::toDomain).toList();
    }

    @Override
    public Optional<Department> load(long id) {
        return departments.findById(id).map(DepartmentEntity::toDomain);
    }

    @Override
    public Optional<Department> findByName(String name) {
        return departments.findByNameIgnoreCase(name).map(DepartmentEntity::toDomain);
    }

    @Override
    public Department save(Department department) {
        DepartmentEntity entity = department.id() == null ? new DepartmentEntity()
                : AuditedEntity.current(departments.findById(department.id()), department.version());
        entity.rename(department.name());
        return departments.saveAndFlush(entity).toDomain();
    }

    @Override
    public void delete(long id) {
        departments.findById(id).ifPresent(departments::delete);
    }
}
