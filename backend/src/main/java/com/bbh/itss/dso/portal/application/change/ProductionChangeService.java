package com.bbh.itss.dso.portal.application.change;

import com.bbh.itss.dso.portal.application.UseCase;
import com.bbh.itss.dso.portal.application.WithoutTransaction;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeEditCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeTasksCommand;
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort;
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort;
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort;
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort.RaisedChange;
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUserUseCase;
import com.bbh.itss.dso.portal.domain.change.ChangeProduct;
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.bbh.itss.dso.portal.domain.change.ChangeSchedule.UNPLANNED;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE;
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.isJiraKey;
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.requested;
import static com.bbh.itss.dso.portal.domain.change.JiraVersion.UNRELEASED_NEWEST_FIRST;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.FIX_VERSION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.checkTemplate;
import static com.bbh.itss.dso.portal.domain.change.ProductionChange.checkTemplateAndSchedule;
import static com.bbh.itss.dso.portal.domain.shared.Failures.notFound;
import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.dso.portal.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.dso.portal.domain.shared.Text.bytes;
import static com.bbh.itss.dso.portal.domain.shared.Timestamps.now;
import static com.bbh.itss.dso.portal.domain.shared.Versions.requireUnchangedSince;
import static java.util.Locale.ROOT;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;
import static org.apache.commons.lang3.StringUtils.trimToNull;
import static org.apache.commons.lang3.Strings.CI;
import static org.apache.commons.lang3.Strings.CS;

@UseCase
@RequiredArgsConstructor
public class ProductionChangeService implements ProductionChangesUseCase {

    private final ChangeProductsPort products;
    private final ProductionChangeRepositoryPort changes;
    private final JiraPort jira;
    private final ServiceNowPort serviceNow;
    private final SignedInUserUseCase users;
    private final Clock clock;

    @Override
    @WithoutTransaction
    public List<ProductionChange> list(Long departmentId) {
        List<ProductionChange> stored = departmentId == null ? changes.findAll()
                : changes.findByDepartment(departmentId);
        List<ProductionChange> open = stored.stream().filter(change -> change.state().isOpen()).toList();
        if (open.isEmpty()) {
            return stored;
        }
        Map<Long, ProductionChange> synced = listed(open).stream()
                .collect(toMap(ProductionChange::id, identity()));
        return stored.stream().map(change -> synced.getOrDefault(change.id(), change)).toList();
    }

    @Override
    @WithoutTransaction
    public ProductionChange get(long id) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        try {
            return synced(stored);
        } catch (UncheckedIOException e) {
            return stored.withSyncProblem(unreachable(e));
        }
    }

    @Override
    @WithoutTransaction
    public ProductionChange update(long id, ChangeEditCommand command) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        requireDepartment(stored, command.departmentId());
        requireUnchangedSince(command.version(), stored.editedVersion(), stored.version());
        Instant now = now(clock);
        ProductionChange edit = stored.edited(command.shortDescription(), command.description(), command.schedule(),
                command.template(), command.tasks(), now);
        ProductionChange current = synced(stored);
        requireChangeable(current, stored);
        ProductionChange claimed = changes.save(edit.rebasedOn(current).toBuilder()
                .update(requested(now, current.departmentName())).build());
        publish(claimed, current);
        ProductionChange verified = verified(claimed, current, now);
        return recorded(verified).withSyncProblem(verified.syncProblem());
    }

    @Override
    @WithoutTransaction
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
        return changes.save(draft.numbered(raised.number(), raised.url()));
    }

    @Override
    @WithoutTransaction
    public ProductionChange createTasks(long id, ChangeTasksCommand command) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        requireDepartment(stored, command.departmentId());
        requireUnchangedSince(command.version(), stored.editedVersion(), stored.version());
        List<ChangeTask> planned = stored.plannedTasks(command.tasks());
        ProductionChange current = synced(stored);
        requireChangeable(current, stored);
        List<ChangeTask> created = new ArrayList<>();
        try {
            planned.forEach(task -> created.add(task.numbered(serviceNow.createTask(current.number(), task))));
        } catch (RuntimeException refused) {
            if (!created.isEmpty()) {
                recorded(current.withTasks(created));
            }
            throw refused;
        }
        return recorded(current.withTasks(created));
    }

    private ProductionChange draft(ChangeCommand command, Instant raisedAt) {
        ChangeProduct product = products.get(command.productId());
        ValidationProblems problems = new ValidationProblems();
        problems.require("productId", product.departmentId(),
                "the product must be placed in a department in Beadle Admin first");
        String version = command.fixVersion();
        problems.require("fixVersion", version, "choose the FixVersion of the release")
                .fits("fixVersion", version, FIX_VERSION_MAX);
        ChangeTemplate template = command.template();
        ChangeSchedule schedule = command.schedule();
        if (raisedAt == null) {
            checkTemplate(template, problems);
        } else {
            checkTemplateAndSchedule(template, schedule, raisedAt, problems);
        }
        problems.require("epicKeys", command.epicKeys(), "choose at least one epic");
        String project = template == null ? null : template.jiraProjectKey();
        List<JiraIssue> epics = List.of();
        List<JiraIssue> stories = List.of();
        if (isJiraKey(project) && version != null && bytes(version) <= FIX_VERSION_MAX) {
            version = jiraName(project, version);
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
        return ProductionChange.draft(product, users.signedInUser().name(), version,
                getIfNull(schedule, UNPLANNED), template, epics, stories, command.shortDescription(),
                command.description());
    }

    private String jiraName(String project, String version) {
        return jira.versions(project).stream().map(JiraVersion::name).filter(name -> CI.equals(name, version))
                .findFirst().orElse(version);
    }

    private List<ProductionChange> listed(List<ProductionChange> open) {
        Map<String, ProductionChange> remote;
        try {
            remote = serviceNow.read(open);
        } catch (UncheckedIOException e) {
            String problem = unreachable(e);
            return open.stream().map(change -> change.withSyncProblem(problem)).toList();
        }
        return synced(open, remote);
    }

    private ProductionChange synced(ProductionChange stored) {
        return synced(List.of(stored), serviceNow.read(List.of(stored))).get(0);
    }

    private List<ProductionChange> synced(List<ProductionChange> open, Map<String, ProductionChange> remote) {
        Instant now = now(clock);
        List<ProductionChange> synced = new ArrayList<>();
        List<Long> unchanged = new ArrayList<>();
        for (ProductionChange stored : open) {
            ProductionChange read = remote.get(stored.number());
            ProductionChange change = read == null ? null : stored.synced(read, now);
            if (change == null) {
                synced.add(stored.withSyncProblem(missing(stored)));
            } else if (change.differsFrom(stored)) {
                synced.add(saved(change, stored));
            } else {
                unchanged.add(stored.id());
                synced.add(change);
            }
        }
        if (!unchanged.isEmpty()) {
            changes.synced(unchanged, now);
        }
        return synced;
    }

    private ProductionChange saved(ProductionChange synced, ProductionChange stored) {
        try {
            return changes.save(synced);
        } catch (IllegalStateException stale) {
            return changes.load(stored.id()).orElseThrow(() -> stale);
        }
    }

    private void publish(ProductionChange claimed, ProductionChange current) {
        try {
            serviceNow.update(claimed);
        } catch (RuntimeException refused) {
            recorded(current.toBuilder().version(claimed.version()).build());
            throw refused;
        }
    }

    private ProductionChange recorded(ProductionChange change) {
        try {
            return changes.save(change);
        } catch (IllegalStateException stale) {
            ProductionChange latest = changes.load(change.id()).orElseThrow(() -> stale);
            return changes.save(change.toBuilder().version(latest.version()).build());
        }
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

    private static void requireChangeable(ProductionChange current, ProductionChange stored) {
        if (current.syncProblem() != null) {
            throw new IllegalStateException(current.syncProblem());
        }
        if (current.update() != null && current.update().pending()) {
            throw new IllegalStateException("The last update of " + current.number()
                    + " is still waiting for ProTech; change it again once ProTech has applied it");
        }
        if (!current.state().isOpen()) {
            throw new IllegalStateException(current.number() + " is closed in ProTech and can no longer be changed");
        }
        if (!current.unappliedIn(stored).isEmpty()) {
            throw staleVersion();
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
        return CS.appendIfMissing("ProTech could not be reached: " + e.getMessage(), ".");
    }

    private static String missing(ProductionChange change) {
        return "ProTech has no change " + change.number() + ".";
    }

    private String projectOf(long productId, String project) {
        ChangeProduct product = products.get(productId);
        String override = trimToNull(project);
        if (override == null) {
            return product.jiraProject();
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
}
