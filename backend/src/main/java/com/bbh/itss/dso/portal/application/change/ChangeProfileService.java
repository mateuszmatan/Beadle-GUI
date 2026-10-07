package com.bbh.itss.dso.portal.application.change;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;

@UseCase
public class ChangeProfileService implements ChangeProfilesUseCase {

    private final ChangeProfileRepositoryPort profiles;
    private final ProductsUseCase products;

    public ChangeProfileService(ChangeProfileRepositoryPort profiles, ProductsUseCase products) {
        this.profiles = profiles;
        this.products = products;
    }

    @Override
    @ReadOnly
    public ChangeProfileView get(long productId) {
        Product product = products.get(productId);
        return profiles.find(productId).map(profile -> view(product, profile))
                .orElseGet(() -> new ChangeProfileView(productId, product.name(), null, null,
                        ChangeTemplate.suggestedFor(product.code(), product.name(), product.ownerTeam(),
                                product.description())));
    }

    @Override
    public ChangeProfileView save(long productId, Long version, ChangeTemplate template) {
        Product product = products.get(productId);
        ChangeProfile changed = profiles.find(productId).map(stored -> stored.change(version, template))
                .orElseGet(() -> ChangeProfile.create(productId, template));
        return view(product, profiles.save(changed));
    }

    private static ChangeProfileView view(Product product, ChangeProfile profile) {
        return new ChangeProfileView(product.id(), product.name(), profile.version(), profile.updatedAt(),
                profile.template());
    }
}
