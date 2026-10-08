package com.bbh.itss.dso.portal.application.change;

import com.bbh.itss.dso.portal.application.ReadOnly;
import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeEditCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations;
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort;
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort.RaisedChange;
import com.bbh.itss.dso.portal.domain.catalog.Product;
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException;
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.isJiraKey;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.jiraKeyOf;
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.requested;
import static com.bbh.itss.dso.portal.domain.change.JiraVersion.UNRELEASED_NEWEST_FIRST;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.FIX_VERSION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.TaskText.validateTasks;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static com.bbh.itss.dso.portal.domain.shared.Timestamps.now;
import static com.bbh.itss.dso.portal.domain.shared.Versions.requireCurrent;
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
    public List<ProductionChange> list(Long departmentId) {
        List<ProductionChange> stored = departmentId == null ? changes.findAll()
                : changes.findByDepartment(departmentId);
        List<ProductionChange> open = stored.stream().filter(change -> change.state().isOpen()).toList();
        if (open.isEmpty()) {
            return stored;
        }
        UnaryOperator<ProductionChange> sync = syncOf(open);
        return stored.stream().map(change -> change.state().isOpen() ? sync.apply(change) : change).toList();
    }

    @Override
    public ProductionChange get(long id) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        try {
            return synced(stored);
        } catch (UncheckedIOException e) {
            return stored.withSyncProblem(unreachable(e));
        }
    }

    @Override
    public ProductionChange update(long id, ChangeEditCommand command) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        requireCurrent(command.version(), stored.version());
        requireDepartment(stored, command.departmentId());
        ProductionChange current = synced(stored);
        if (!current.state().isOpen()) {
            throw new IllegalStateException(current.number() + " is closed in ProTech and can no longer be changed");
        }
        Instant now = now(clock);
        ProductionChange edited = current.edited(command.shortDescription(), command.description(),
                command.schedule(), command.template(), command.tasks(), now);
        serviceNow.update(edited);
        ProductionChange published = edited.toBuilder().update(requested(now, current.departmentName())).build();
        ProductionChange verified = verified(published, current, now);
        return changes.save(verified).withSyncProblem(verified.syncProblem());
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
        return draft(command, null);
    }

    @Override
    @WithoutTransaction
    public ProductionChange raise(ChangeCommand command) {
        Instant now = now(clock);
        ProductionChange draft = draft(command, now).raisedAt(now);
        RaisedChange raised = serviceNow.raise(draft);
        return changes.save(draft.numbered(raised.number(), raised.taskNumbers(), raised.url()));
    }

    private ProductionChange draft(ChangeCommand command, Instant raisedAt) {
        Product product = products.get(command.productId());
        ValidationProblems problems = new ValidationProblems();
        String version = trimToNull(command.fixVersion());
        problems.require("fixVersion", version, "choose the FixVersion of the release")
                .fits("fixVersion", version, FIX_VERSION_MAX);
        ChangeTemplate template = command.template();
        problems.require("template", template, "fill in the ProTech fields of the change");
        if (template != null) {
            template.validate(problems.at("template"));
        }
        ChangeSchedule schedule = command.schedule();
        problems.require("schedule", schedule, "choose when the change is installed, validated and first used");
        if (schedule != null) {
            schedule.check(problems.at("schedule"));
            if (raisedAt != null) {
                schedule.checkUpcoming(raisedAt, problems.at("schedule"));
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
        validateTasks(command.tasks(), problems);
        problems.throwIfAny();
        return ProductionChange.draft(product, product.departmentId(), departmentOf(product), command.tasks(),
                command.fixVersion(), schedule, template, epics, stories, command.shortDescription(),
                command.description());
    }

    private UnaryOperator<ProductionChange> syncOf(List<ProductionChange> open) {
        try {
            Map<String, ProductionChange> remote = serviceNow.read(open);
            Instant now = now(clock);
            return change -> synced(change, remote, now);
        } catch (UncheckedIOException e) {
            String problem = unreachable(e);
            return change -> change.withSyncProblem(problem);
        }
    }

    private ProductionChange synced(ProductionChange stored) {
        return synced(stored, serviceNow.read(List.of(stored)), now(clock));
    }

    private ProductionChange synced(ProductionChange stored, Map<String, ProductionChange> remote, Instant now) {
        ProductionChange read = remote.get(stored.number());
        if (read == null) {
            return stored.withSyncProblem(missing(stored));
        }
        ProductionChange synced = stored.synced(read, now);
        if (synced.differsFrom(stored)) {
            return changes.save(synced);
        }
        changes.synced(stored.id(), now);
        return synced;
    }

    private ProductionChange verified(ProductionChange published, ProductionChange current, Instant now) {
        try {
            ProductionChange remote = serviceNow.read(List.of(published)).get(published.number());
            return remote == null ? published.unverified(current, missing(published))
                    : published.synced(remote, now);
        } catch (UncheckedIOException e) {
            return published.unverified(current, unreachable(e));
        }
    }

    private static void requireDepartment(ProductionChange stored, Long departmentId) {
        if (departmentId == null) {
            throw InvalidRequestException.of("departmentId", "choose your department");
        }
        if (stored.departmentId() == null) {
            throw new SecurityException("No department owns " + stored.number()
                    + ", so it cannot be changed in Beadle");
        }
        if (!stored.departmentId().equals(departmentId)) {
            throw new SecurityException("Only " + stored.departmentName() + " can change " + stored.number());
        }
    }

    private static String unreachable(UncheckedIOException e) {
        return "ProTech could not be reached: " + e.getMessage();
    }

    private static String missing(ProductionChange change) {
        return "ProTech has no change " + change.number();
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
