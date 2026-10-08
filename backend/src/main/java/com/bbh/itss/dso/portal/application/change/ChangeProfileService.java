package com.bbh.itss.dso.portal.application.change;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.dso.portal.domain.change.ChangeProduct;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.TaskText;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor;
import static com.bbh.itss.dso.portal.domain.change.TaskText.suggestedTasks;
import static com.bbh.itss.dso.portal.domain.change.TaskText.validateTasks;

@UseCase
@RequiredArgsConstructor
public class ChangeProfileService implements ChangeProfilesUseCase {

    private final ChangeProfileRepositoryPort profiles;
    private final ChangeProductsPort products;

    @Override
    @ReadOnly
    public List<ChangeProfileSummary> list() {
        return profiles.summaries();
    }

    @Override
    @ReadOnly
    public ChangeProfileView get(long productId) {
        ChangeProduct product = products.get(productId);
        return profiles.find(productId).map(profile -> view(product, profile))
                .orElseGet(() -> ChangeProfileView.builder().productId(productId).productName(product.name())
                        .template(suggestedFor(product.code(), product.name(), product.ownerTeam(),
                                product.description()))
                        .tasks(suggestedTasks(product.name())).build());
    }

    @Override
    public ChangeProfileView save(long productId, Long version, ChangeTemplate template, List<TaskText> tasks) {
        ChangeProduct product = products.get(productId);
        ValidationProblems problems = new ValidationProblems();
        template.validate(problems.at("template"));
        validateTasks(tasks, problems);
        problems.throwIfAny();
        ChangeProfile changed = profiles.find(productId).map(stored -> stored.change(version, template, tasks))
                .orElseGet(() -> ChangeProfile.create(productId, template, tasks));
        return view(product, profiles.save(changed));
    }

    private static ChangeProfileView view(ChangeProduct product, ChangeProfile profile) {
        return new ChangeProfileView(product.id(), product.name(), profile.version(), profile.updatedAt(),
                profile.template(), profile.tasks());
    }
}
