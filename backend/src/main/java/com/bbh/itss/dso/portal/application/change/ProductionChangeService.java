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
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.isJiraKey;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.jiraKeyOf;
import static com.bbh.itss.dso.portal.domain.change.JiraVersion.UNRELEASED_NEWEST_FIRST;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.FIX_VERSION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static java.util.Locale.ROOT;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.trimToNull;

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
    public List<JiraVersion> versions(long productId, String project) {
        return jira.versions(projectOf(productId, project)).stream()
                .sorted(UNRELEASED_NEWEST_FIRST).toList();
    }

    @Override
    @WithoutTransaction
    public List<JiraIssue> epics(long productId, String fixVersion, String project) {
        String key = projectOf(productId, project);
        return jira.epics(key, required(fixVersion));
    }

    @Override
    @WithoutTransaction
    public List<JiraIssue> stories(long productId, String fixVersion, List<String> epicKeys, String project) {
        String key = projectOf(productId, project);
        String version = required(fixVersion);
        return epicKeys.isEmpty() ? List.of() : jira.stories(key, version, epicKeys);
    }

    @Override
    @WithoutTransaction
    public ProductionChange preview(ChangeCommand command) {
        return draft(command, false);
    }

    @Override
    @WithoutTransaction
    public ProductionChange raise(ChangeCommand command) {
        ProductionChange draft = draft(command, true);
        RaisedChange raised = serviceNow.raise(draft);
        return changes.save(draft.numbered(raised.number(), raised.taskNumbers(), raised.url()));
    }

    private ProductionChange draft(ChangeCommand command, boolean raising) {
        Product product = products.get(command.productId());
        ValidationProblems problems = new ValidationProblems();
        List<Service> services = product.services().stream()
                .filter(service -> command.serviceIds().isEmpty() || command.serviceIds().contains(service.id()))
                .toList();
        if (product.services().isEmpty()) {
            problems.add("serviceIds", product.name() + " has no services to deploy");
        }
        command.serviceIds().stream().filter(id -> product.service(id).isEmpty()).forEach(id ->
                problems.add("serviceIds", "service " + id + " is not a service of " + product.name()));
        String version = trimToNull(command.fixVersion());
        problems.require("fixVersion", version, "choose the FixVersion of the release")
                .fits("fixVersion", version, FIX_VERSION_MAX);
        ChangeTemplate template = command.template();
        problems.require("template", template, "fill in the ServiceNow fields of the change");
        if (template != null) {
            template.validate(problems.at("template"));
        }
        ChangeSchedule schedule = command.schedule();
        problems.require("schedule", schedule, "choose when the change is installed, validated and first used");
        if (schedule != null) {
            schedule.check(problems.at("schedule"));
            if (raising) {
                schedule.checkUpcoming(clock.instant(), problems.at("schedule"));
            }
        }
        problems.require("epicKeys", command.epicKeys(), "choose at least one epic");
        String project = template == null ? null : template.jiraProjectKey();
        List<JiraIssue> epics = List.of();
        List<JiraIssue> stories = List.of();
        if (isJiraKey(project) && version != null && bytes(version) <= FIX_VERSION_MAX) {
            epics = chosen(command.epicKeys(), jira.epics(project, version), "epicKeys",
                    " is not an epic of FixVersion " + version + " in Jira project " + project, problems);
            List<String> epicKeys = epics.stream().map(JiraIssue::key).toList();
            List<JiraIssue> offered = epicKeys.isEmpty() || command.storyKeys().isEmpty() ? List.of()
                    : jira.stories(project, version, epicKeys);
            stories = chosen(command.storyKeys(), offered, "storyKeys",
                    " is not a story of the chosen epics in FixVersion " + version, problems);
        }
        LINES_1000.check(problems, "epicKeys", command.epicKeys());
        LINES_4000.check(problems, "storyKeys", command.storyKeys());
        problems.fits("shortDescription", command.shortDescription(), SHORT_DESCRIPTION_MAX)
                .fits("description", command.description(), DESCRIPTION_MAX);
        problems.throwIfAny();
        return ProductionChange.draft(product, departmentOf(product), services, command.fixVersion(), schedule,
                template, epics, stories, command.shortDescription(), command.description());
    }

    private String projectOf(long productId, String project) {
        Product product = products.get(productId);
        String override = trimToNull(project);
        if (override == null) {
            return profiles.find(productId).map(profile -> profile.template().jiraProjectKey())
                    .orElseGet(() -> jiraKeyOf(product.code()));
        }
        String key = override.toUpperCase(ROOT);
        if (!isJiraKey(key)) {
            throw InvalidRequestException.of("project", JIRA_KEY_MESSAGE);
        }
        return key;
    }

    private static String required(String fixVersion) {
        String version = trimToNull(fixVersion);
        if (version == null) {
            throw InvalidRequestException.of("fixVersion", "choose a FixVersion");
        }
        return version;
    }

    private static List<JiraIssue> chosen(List<String> keys, List<JiraIssue> offered, String field, String refusal,
                                          ValidationProblems problems) {
        Map<String, JiraIssue> found = offered.stream()
                .collect(toMap(JiraIssue::key, identity(), (first, second) -> first));
        keys.stream().filter(key -> !found.containsKey(key)).forEach(key -> problems.add(field, key + refusal));
        return keys.stream().map(found::get).filter(Objects::nonNull).toList();
    }

    private String departmentOf(Product product) {
        return departments.list().stream()
                .filter(department -> Objects.equals(department.id(), product.departmentId()))
                .map(DepartmentView::name).findFirst().orElse(null);
    }
}
