package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.beadle.domain.catalog.BusinessService;
import com.bbh.itss.beadle.domain.catalog.Department;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DepartmentPersistenceAdapter implements DepartmentRepositoryPort {

    private final BusinessServicePersistenceAdapter businessServices;

    @Override
    public List<Department> findAll() {
        return businessServices.findAll().stream().map(DepartmentPersistenceAdapter::toDept).toList();
    }

    @Override
    public Optional<Department> load(long id) {
        return businessServices.load(id).map(DepartmentPersistenceAdapter::toDept);
    }

    @Override
    public Optional<Department> findByName(String name) {
        return businessServices.findByName(name).map(DepartmentPersistenceAdapter::toDept);
    }

    @Override
    public Department save(Department department) {
        BusinessService saved = businessServices.save(new BusinessService(department.id(), department.name(), department.version()));
        return toDept(saved);
    }

    @Override
    public void delete(long id) {
        businessServices.delete(id);
    }

    private static Department toDept(BusinessService bs) {
        return new Department(bs.id(), bs.name(), bs.version());
    }
}
