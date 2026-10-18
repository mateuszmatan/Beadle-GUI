package com.bbh.itss.beadle.application.catalog;

import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.ReadOnly;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.catalog.port.in.ProductCommand;
import com.bbh.itss.beadle.application.catalog.port.in.ProductView;
import com.bbh.itss.beadle.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceRepositoryPort;
import com.bbh.itss.beadle.application.catalog.port.out.ProductRepositoryPort;
import com.bbh.itss.beadle.domain.catalog.BusinessService;
import com.bbh.itss.beadle.domain.catalog.Product;
import com.bbh.itss.beadle.domain.catalog.ProductDetails;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_DELETED;
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_UPDATED;
import static com.bbh.itss.beadle.domain.catalog.ProductCode.suggest;
import static com.bbh.itss.beadle.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.domain.shared.Text.matches;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

@UseCase
@RequiredArgsConstructor
public class ProductService implements ProductsUseCase {

    private final ProductRepositoryPort products;
    private final BusinessServiceRepositoryPort businessServices;

    @Override
    @ReadOnly
    public List<ProductView> list(String search) {
        Map<Long, String> names = businessServiceNames();
        return products.findAll().stream()
                .map(product -> view(product, names))
                .filter(view -> matches(search, view.name(), view.code(), view.ownerTeam(), view.businessServiceName()))
                .sorted(comparing(ProductView::name, CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @ReadOnly
    public ProductView get(long id) {
        return view(find(id), businessServiceNames());
    }

    @Override
    @Audited(PRODUCT_CREATED)
    public ProductView create(ProductCommand command) {
        return view(products.save(Product.create(command.details(), products)), businessServiceNames());
    }

    @Override
    @Audited(PRODUCT_UPDATED)
    public ProductView update(long id, ProductCommand command) {
        Product changed = find(id).change(command.version(), command.details(), products);
        return view(products.save(changed), businessServiceNames());
    }

    @Override
    @Audited(PRODUCT_DELETED)
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

    private Map<Long, String> businessServiceNames() {
        return businessServices.findAll().stream().collect(toMap(BusinessService::id, BusinessService::name));
    }

    private static ProductView view(Product product, Map<Long, String> businessServiceNames) {
        ProductDetails details = product.details();
        return new ProductView(product.id(), details.code(), details.name(), details.ownerTeam(),
                details.contactEmail(), details.businessServiceId(),
                businessServiceNames.get(details.businessServiceId()), product.version(), product.updatedAt());
    }
}
