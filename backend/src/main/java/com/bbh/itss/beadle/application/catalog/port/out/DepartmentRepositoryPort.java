package com.bbh.itss.beadle.application.catalog.port.out;

import com.bbh.itss.beadle.domain.catalog.Department;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepositoryPort {

    List<Department> findAll();

    Optional<Department> load(long id);

    Optional<Department> findByName(String name);

    Department save(Department department);

    void delete(long id);
}
