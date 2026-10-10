package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentUsagePort;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.catalog.DepartmentUsage;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;

@UseCase
@RequiredArgsConstructor
public class DepartmentService implements DepartmentsUseCase {

    private final DepartmentRepositoryPort departments;
    private final DepartmentUsagePort usage;

    @Override
    @ReadOnly
    public List<DepartmentView> list() {
        Map<Long, DepartmentUsage> perDepartment = usage.perDepartment();
        return departments.findAll().stream()
                .sorted(comparing(Department::name, CASE_INSENSITIVE_ORDER))
                .map(department -> new DepartmentView(department.id(), department.name(), department.version(),
                        perDepartment.getOrDefault(department.id(), usage.unused())))
                .toList();
    }

    @Override
    public DepartmentView create(String name) {
        return view(departments.save(Department.create(name, departments::findByName)).id());
    }

    @Override
    public DepartmentView rename(long id, Long version, String name) {
        Department department = departments.load(id).orElseThrow(() -> notFound("Department", id));
        departments.save(department.rename(version, name, departments::findByName));
        return view(id);
    }

    @Override
    public void delete(long id) {
        DepartmentView department = view(id);
        long products = department.usage().productCount();
        if (products > 0) {
            throw new IllegalStateException(department.name() + " still has " + products
                    + " product(s). Move them to another department first.");
        }
        department.usage().deletionRefusal(department.name()).ifPresent(refusal -> {
            throw new IllegalStateException(refusal);
        });
        departments.delete(id);
    }

    private DepartmentView view(long id) {
        return list().stream().filter(department -> department.id() == id).findFirst()
                .orElseThrow(() -> notFound("Department", id));
    }
}
