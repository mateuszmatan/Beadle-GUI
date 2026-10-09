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
import com.bbh.itss.dso.portal.domain.change.TaskDetails;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor;
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.suggestedTasks;
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.validateTasks;

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
        return profiles.find(productId).map(profile -> view(product, profile)).orElseGet(() -> suggested(product));
    }

    private static ChangeProfileView suggested(ChangeProduct product) {
        ChangeTemplate template = suggestedFor(product.code(), product.name(), product.ownerTeam());
        return ChangeProfileView.builder().productId(product.id()).productName(product.name()).template(template)
                .tasks(suggestedTasks(product.name(), template.assignmentGroup())).build();
    }

    @Override
    public ChangeProfileView save(long productId, Long version, ChangeTemplate template, List<TaskDetails> tasks) {
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
