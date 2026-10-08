package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.ChangeCountsPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.PipelineCountsPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductSummary;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@UseCase
@RequiredArgsConstructor
public class DepartmentService implements DepartmentsUseCase {

    private final DepartmentRepositoryPort departments;
    private final ProductRepositoryPort products;
    private final PipelineCountsPort pipelineCounts;
    private final ChangeCountsPort changeCounts;

    @Override
    @ReadOnly
    public List<DepartmentView> list() {
        Map<Long, List<Long>> productIds = products.summaries().stream()
                .filter(product -> product.departmentId() != null)
                .collect(groupingBy(ProductSummary::departmentId, mapping(ProductSummary::id, toList())));
        Map<Long, Long> services = products.servicesPerProduct();
        Map<Long, Long> pipelines = pipelineCounts.pipelinesPerProduct();
        Map<Long, Long> active = pipelineCounts.activePipelinesPerProduct();
        Map<Long, Long> raised = changeCounts.changesPerDepartment();
        return departments.findAll().stream()
                .sorted(comparing(Department::name, CASE_INSENSITIVE_ORDER))
                .map(department -> {
                    List<Long> ids = productIds.getOrDefault(department.id(), List.of());
                    return new DepartmentView(department.id(), department.name(), department.version(), ids.size(),
                            total(ids, services), total(ids, pipelines), total(ids, active),
                            raised.getOrDefault(department.id(), 0L));
                })
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
        if (department.productCount() > 0) {
            throw new IllegalStateException(department.name() + " still has " + department.productCount()
                    + " product(s). Move them to another department first.");
        }
        if (department.changeCount() > 0) {
            throw new IllegalStateException(department.name() + " still owns " + department.changeCount()
                    + " change(s) raised in Beadle, so it cannot be deleted.");
        }
        departments.delete(id);
    }

    private DepartmentView view(long id) {
        return list().stream().filter(department -> department.id() == id).findFirst()
                .orElseThrow(() -> notFound("Department", id));
    }

    private static long total(List<Long> productIds, Map<Long, Long> counts) {
        return productIds.stream().mapToLong(id -> counts.getOrDefault(id, 0L)).sum();
    }
}
