package com.bbh.itss.dso.portal.application.catalog;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.out.DepartmentRepositoryPort;
import com.bbh.itss.dso.portal.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Department;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.ProductDetails;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.domain.catalog.ProductCode.suggest;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static com.bbh.itss.dso.portal.domain.shared.Text.matches;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

@UseCase
@RequiredArgsConstructor
public class ProductService implements ProductsUseCase {

    private final ProductRepositoryPort products;
    private final DepartmentRepositoryPort departments;

    @Override
    @ReadOnly
    public List<ProductView> list(String search) {
        Map<Long, String> names = departmentNames();
        return products.findAll().stream()
                .map(product -> view(product, names))
                .filter(view -> matches(search, view.name(), view.code(), view.ownerTeam(), view.departmentName()))
                .sorted(comparing(ProductView::name, CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @ReadOnly
    public ProductView get(long id) {
        return view(find(id), departmentNames());
    }

    @Override
    public ProductView create(ProductCommand command) {
        return view(products.save(Product.create(command.details(), products)), departmentNames());
    }

    @Override
    public ProductView update(long id, ProductCommand command) {
        Product changed = find(id).change(command.version(), command.details(), products);
        return view(products.save(changed), departmentNames());
    }

    @Override
    public void delete(long id) {
        find(id);
        products.delete(id);
    }

    @Override
    @ReadOnly
    public String suggestCode(String name) {
        return suggest(name, products);
    }

    private Product find(long id) {
        return products.load(id).orElseThrow(() -> notFound("Product", id));
    }

    private Map<Long, String> departmentNames() {
        return departments.findAll().stream().collect(toMap(Department::id, Department::name));
    }

    private static ProductView view(Product product, Map<Long, String> departmentNames) {
        ProductDetails details = product.details();
        return new ProductView(product.id(), details.code(), details.name(), details.ownerTeam(),
                details.contactEmail(), details.departmentId(), departmentNames.get(details.departmentId()),
                product.version(), product.updatedAt());
    }
}
