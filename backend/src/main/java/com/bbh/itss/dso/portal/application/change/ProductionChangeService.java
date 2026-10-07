package com.bbh.itss.dso.portal.application.change;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations;
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort;
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort.RaisedChange;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.catalog.Service;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeWindow;
import com.bbh.itss.dso.portal.domain.change.DateRange;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

@UseCase
@RequiredArgsConstructor
public class ProductionChangeService implements ProductionChangesUseCase {

    private final ProductsUseCase products;
    private final DepartmentsUseCase departments;
    private final ChangeProfileRepositoryPort profiles;
    private final ProductionChangeRepositoryPort changes;
    private final JiraPort jira;
    private final ServiceNowPort serviceNow;
    private final Clock clock;

    @Override
    @ReadOnly
    public List<ProductionChange> list() {
        return changes.findAll();
    }

    @Override
    @ReadOnly
    public ProductionChange get(long id) {
        return changes.load(id).orElseThrow(() -> notFound("Change", id));
    }

    @Override
    @ReadOnly
    public ChangeIntegrations integrations() {
        return new ChangeIntegrations(jira.connected(), serviceNow.connected());
    }

    @Override
    @WithoutTransaction
    public List<JiraIssue> epics(long productId, DateRange updated) {
        return jira.epics(templateOf(products.get(productId)).jiraProjectKey(), updated);
    }

    @Override
    @WithoutTransaction
    public List<JiraIssue> stories(long productId, List<String> epicKeys, DateRange updated) {
        String project = templateOf(products.get(productId)).jiraProjectKey();
        return epicKeys.isEmpty() ? List.of() : jira.stories(project, epicKeys, updated);
    }

    @Override
    @WithoutTransaction
    public ProductionChange preview(ChangeCommand command) {
        return draft(command);
    }

    @Override
    @WithoutTransaction
    public ProductionChange raise(ChangeCommand command) {
        ProductionChange draft = draft(command);
        RaisedChange raised = serviceNow.raise(draft);
        return changes.save(draft.numbered(raised.number(), raised.taskNumbers(), raised.url()));
    }

    private ProductionChange draft(ChangeCommand command) {
        Product product = products.get(command.productId());
        ChangeTemplate template = templateOf(product);
        String project = template.jiraProjectKey();
        ValidationProblems problems = new ValidationProblems();
        List<Service> services = product.services().stream()
                .filter(service -> command.serviceIds().contains(service.id())).toList();
        problems.require("serviceIds", command.serviceIds(), "choose at least one service");
        command.serviceIds().stream().filter(id -> product.service(id).isEmpty()).forEach(id ->
                problems.add("serviceIds", "service " + id + " is not a service of " + product.name()));
        problems.require("epicKeys", command.epicKeys(), "choose at least one epic");
        Map<String, JiraIssue> found = jira.issues(project,
                        Stream.concat(command.epicKeys().stream(), command.storyKeys().stream()).toList()).stream()
                .collect(toMap(JiraIssue::key, identity(), (first, second) -> first));
        List<JiraIssue> epics = chosen(command.epicKeys(), found, "epicKeys", project, problems);
        epics.stream().filter(epic -> epic.epicKey() != null).forEach(epic ->
                problems.add("epicKeys", epic.key() + " is a story, not an epic"));
        List<JiraIssue> stories = chosen(command.storyKeys(), found, "storyKeys", project, problems);
        stories.stream().filter(story -> !command.epicKeys().contains(story.epicKey())).forEach(story ->
                problems.add("storyKeys", story.key() + " is not a story of the chosen epics"));
        LINES_1000.check(problems, "epicKeys", command.epicKeys());
        LINES_4000.check(problems, "storyKeys", command.storyKeys());
        fits(problems, "shortDescription", command.shortDescription(), SHORT_DESCRIPTION_MAX);
        fits(problems, "description", command.description(), DESCRIPTION_MAX);
        ChangeWindow window = new ChangeWindow(command.start(), command.end());
        window.check(clock.instant(), problems);
        problems.throwIfAny();
        return ProductionChange.draft(product, departmentOf(product), services, template, window, epics, stories,
                command.shortDescription(), command.description());
    }

    private static void fits(ValidationProblems problems, String field, String text, int maxBytes) {
        if (bytes(trimToEmpty(text)) > maxBytes) {
            problems.add(field, "is too long: it may take at most " + maxBytes + " bytes");
        }
    }

    private static List<JiraIssue> chosen(List<String> keys, Map<String, JiraIssue> found, String field,
                                          String project, ValidationProblems problems) {
        keys.stream().filter(key -> !found.containsKey(key)).forEach(key ->
                problems.add(field, key + " is not in Jira project " + project));
        return keys.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private ChangeTemplate templateOf(Product product) {
        return profiles.find(product.id()).map(ChangeProfile::template).orElseThrow(() -> new IllegalStateException(
                product.name() + " has no ServiceNow change template yet. Fill it in under DevSecOps Product"
                        + " Management first."));
    }

    private String departmentOf(Product product) {
        return departments.list().stream()
                .filter(department -> Objects.equals(department.id(), product.departmentId()))
                .map(DepartmentView::name).findFirst().orElse(null);
    }
}
