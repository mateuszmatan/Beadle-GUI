package com.bbh.itss.beadle.application.change;

import com.bbh.itss.beadle.application.AuditNotes;
import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.WithoutTransaction;
import com.bbh.itss.beadle.application.change.port.in.ChangeCommand;
import com.bbh.itss.beadle.application.change.port.in.ChangeEditCommand;
import com.bbh.itss.beadle.application.change.port.in.ChangeIntegrations;
import com.bbh.itss.beadle.application.change.port.in.ChangeTasksCommand;
import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.beadle.application.change.port.in.ReminderCommand;
import com.bbh.itss.beadle.application.change.port.in.SecureCodingCommand;
import com.bbh.itss.beadle.application.change.port.out.ChangeProductsPort;
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort;
import com.bbh.itss.beadle.application.change.port.out.JiraPort;
import com.bbh.itss.beadle.application.change.port.out.ProTechPort;
import com.bbh.itss.beadle.application.change.port.out.ProTechPort.CreatedChange;
import com.bbh.itss.beadle.application.change.port.out.ProductionChangeRepositoryPort;
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import com.bbh.itss.beadle.domain.change.ApprovalRef;
import com.bbh.itss.beadle.domain.change.ChangeProduct;
import com.bbh.itss.beadle.domain.change.ChangeSchedule;
import com.bbh.itss.beadle.domain.change.ChangeTask;
import com.bbh.itss.beadle.domain.change.ChangeTemplate;
import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;
import com.bbh.itss.beadle.domain.change.ProductionChange;
import com.bbh.itss.beadle.domain.change.Reminder;
import com.bbh.itss.beadle.domain.change.SecureCodingTicket;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.ValidationProblems;
import lombok.RequiredArgsConstructor;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_EDITED;
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_PREVIEWED;
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_SYNCED;
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_TASKS_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.REMINDER_SENT;
import static com.bbh.itss.beadle.domain.audit.Action.SECURE_CODING_TICKET_CREATED;
import static com.bbh.itss.beadle.domain.change.ChangeSchedule.UNPLANNED;
import static com.bbh.itss.beadle.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE;
import static com.bbh.itss.beadle.domain.change.ChangeTemplate.isJiraKey;
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.requested;
import static com.bbh.itss.beadle.domain.change.JiraVersion.UNRELEASED_NEWEST_FIRST;
import static com.bbh.itss.beadle.domain.change.ProductionChange.DESCRIPTION_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.FIX_VERSION_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.SHORT_DESCRIPTION_MAX;
import static com.bbh.itss.beadle.domain.change.ProductionChange.checkReminder;
import static com.bbh.itss.beadle.domain.change.ProductionChange.checkTemplate;
import static com.bbh.itss.beadle.domain.change.ProductionChange.checkTemplateAndSchedule;
import static com.bbh.itss.beadle.domain.shared.Failures.notFound;
import static com.bbh.itss.beadle.domain.shared.Failures.staleVersion;
import static com.bbh.itss.beadle.domain.shared.StoredList.LINES_1000;
import static com.bbh.itss.beadle.domain.shared.StoredList.LINES_4000;
import static com.bbh.itss.beadle.domain.shared.Text.bytes;
import static com.bbh.itss.beadle.domain.shared.Timestamps.now;
import static com.bbh.itss.beadle.domain.shared.Versions.requireUnchangedSince;
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
    private final ProTechPort proTech;
    private final CyberTrackPort cyberTrack;
    private final SignedInUserPort users;
    private final Clock clock;

    @Override
    @WithoutTransaction
    @Audited(value = CHANGE_SYNCED, onlyWhenNoted = true)
    public List<ProductionChange> list(Long businessServiceId) {
        List<ProductionChange> stored = businessServiceId == null ? changes.findAll()
                : changes.findByBusinessService(businessServiceId);
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
    @Audited(value = CHANGE_SYNCED, onlyWhenNoted = true)
    public ProductionChange get(long id) {
        return listed(List.of(changes.load(id).orElseThrow(() -> notFound("Change", id)))).get(0);
    }

    @Override
    @WithoutTransaction
    public Optional<ProductionChange> resync(String number) {
        return changes.findByNumber(number).map(this::synced);
    }

    @Override
    @WithoutTransaction
    @Audited(CHANGE_EDITED)
    public ProductionChange update(long id, ChangeEditCommand command) {
        ProductionChange stored = editable(id, command.version(), command.businessServiceId());
        Instant now = now(clock);
        ProductionChange edit = stored.edited(command.shortDescription(), command.description(), command.schedule(),
                command.template(), command.tasks(), now);
        ProductionChange current = changeable(stored);
        return published(edit.rebasedOn(current), current, now);
    }

    @Override
    @WithoutTransaction
    public ChangeIntegrations integrations() {
        return new ChangeIntegrations(jira.jiraConnected(), proTech.proTechConnected(),
                cyberTrack.cyberTrackConnected(), jira.reachable() && cyberTrack.reachable(), proTech.reachable());
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
    @Audited(CHANGE_PREVIEWED)
    public ProductionChange preview(ChangeCommand command) {
        return draft(command, null);
    }

    @Override
    @WithoutTransaction
    @Audited(CHANGE_CREATED)
    public ProductionChange create(ChangeCommand command) {
        Instant now = now(clock);
        ProductionChange draft = draft(command, now).created(now);
        CreatedChange created = proTech.create(draft);
        AuditNotes.created(created.number());
        return changes.save(draft.numbered(created.number(), created.url()));
    }

    @Override
    @WithoutTransaction
    @Audited(CHANGE_TASKS_CREATED)
    public ProductionChange createTasks(long id, ChangeTasksCommand command) {
        ProductionChange stored = editable(id, command.version(), command.businessServiceId());
        List<ChangeTask> planned = stored.plannedTasks(command.tasks());
        ProductionChange current = changeable(stored);
        List<ChangeTask> created = new ArrayList<>();
        try {
            planned.forEach(task -> {
                String number = proTech.createTask(current.number(), task);
                AuditNotes.created(number);
                created.add(task.withNumber(number));
            });
        } catch (RuntimeException refused) {
            if (!created.isEmpty()) {
                recorded(current.withTasks(created));
            }
            throw refused;
        }
        return listed(List.of(recorded(current.withTasks(created)))).get(0);
    }

    @Override
    @WithoutTransaction
    @Audited(SECURE_CODING_TICKET_CREATED)
    public ProductionChange createSecureCodingTicket(long id, SecureCodingCommand command) {
        ProductionChange stored = editable(id, command.version(), command.businessServiceId());
        SecureCodingTicket ticket = SecureCodingTicket.of(stored, command.secureCoding(),
                command.implementationDate());
        ProductionChange current = changeable(stored);
        String existing = current.template().secureCodingTicket();
        if (existing != null) {
            throw new IllegalStateException(current.number() + " already has the secure coding ticket " + existing);
        }
        String key = cyberTrack.createTicket(ticket);
        AuditNotes.created(key);
        try {
            return published(current.withSecureCoding(command.secureCoding(), key), current, now(clock));
        } catch (RuntimeException refused) {
            throw new IllegalStateException(key + " was created in CyberTrack, but " + current.number()
                    + " could not take it: " + refused.getMessage() + " Enter " + key
                    + " as the secure coding ticket with Edit the change.", refused);
        }
    }

    @Override
    @WithoutTransaction
    @Audited(REMINDER_SENT)
    public ProductionChange remind(long id, ReminderCommand command) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        requireBusinessService(stored, command.businessServiceId(), "remind the approvers of");
        checkReminder(command.approval(), command.task());
        ProductionChange current = synced(stored);
        if (current.syncProblem() != null) {
            throw new IllegalStateException(current.syncProblem());
        }
        List<ApprovalRef> awaited = current.toRemind(command.approval(), command.task());
        Instant now = now(clock);
        Map<ApprovalRef, Reminder> sent = new LinkedHashMap<>();
        List<Map<String, Object>> reminded = new ArrayList<>();
        try {
            awaited.forEach(approval -> {
                List<String> people = proTech.remind(current.number(), approval);
                sent.put(approval, new Reminder(now, people));
                reminded.add(reminded(approval, people));
                AuditNotes.result("reminded", List.copyOf(reminded));
            });
        } catch (RuntimeException refused) {
            if (!sent.isEmpty()) {
                remembered(current, sent);
            }
            throw refused;
        }
        return remembered(current, sent);
    }

    /** Who ProTech reminded of an approval, as the audit trail records it. */
    private static Map<String, Object> reminded(ApprovalRef approval, List<String> people) {
        Map<String, Object> reminded = new LinkedHashMap<>();
        reminded.put("role", approval.role());
        reminded.put("task", approval.task());
        reminded.put("people", people);
        return reminded;
    }

    private ProductionChange remembered(ProductionChange change, Map<ApprovalRef, Reminder> sent) {
        return savedOnLatest(change, latest -> latest.reminded(sent));
    }

    private ProductionChange editable(long id, Long version, Long businessServiceId) {
        ProductionChange stored = changes.load(id).orElseThrow(() -> notFound("Change", id));
        requireBusinessService(stored, businessServiceId, "change");
        requireUnchangedSince(version, stored.editedVersion(), stored.version());
        return stored;
    }

    private ProductionChange changeable(ProductionChange stored) {
        ProductionChange current = synced(stored);
        requireChangeable(current, stored);
        return current;
    }

    private ProductionChange published(ProductionChange edit, ProductionChange current, Instant now) {
        ProductionChange claimed = changes.save(edit.toBuilder().update(requested(now, current.businessServiceName()))
                .build());
        publish(claimed, current);
        ProductionChange verified = verified(claimed, current, now);
        return recorded(verified).withSyncProblem(verified.syncProblem());
    }

    private ProductionChange draft(ChangeCommand command, Instant createdAt) {
        ChangeProduct product = products.get(command.productId());
        ValidationProblems problems = new ValidationProblems();
        problems.require("productId", product.businessServiceId(),
                "the product must be placed in a business service in Beadle Admin first");
        String version = command.fixVersion();
        problems.require("fixVersion", version, "choose the FixVersion of the release")
                .fits("fixVersion", version, FIX_VERSION_MAX);
        ChangeTemplate template = command.template();
        ChangeSchedule schedule = command.schedule();
        if (createdAt == null) {
            checkTemplate(template, problems);
        } else {
            checkTemplateAndSchedule(template, schedule, createdAt, problems);
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
        return ProductionChange.draft(product, users.name(), version,
                getIfNull(schedule, UNPLANNED), template, epics, stories, command.shortDescription(),
                command.description());
    }

    private String jiraName(String project, String version) {
        return jira.versions(project).stream().map(JiraVersion::name).filter(name -> CI.equals(name, version))
                .findFirst().orElse(version);
    }

    /**
     * The changes synced with one read of ProTech, or as stored with the sync problem when ProTech cannot be reached or
     * refuses the read (a {@code 4xx} of Beadle-ProTech, such as for a number it does not know), so that one change
     * cannot keep the others from being shown. The changes of a write are read with {@link #synced(ProductionChange)},
     * which lets every failure through.
     */
    private List<ProductionChange> listed(List<ProductionChange> open) {
        Map<String, ProductionChange> remote;
        try {
            remote = proTech.read(open);
        } catch (UncheckedIOException | InvalidRequestException | IllegalStateException | NoSuchElementException e) {
            String problem = syncProblem(e);
            return open.stream().map(change -> change.withSyncProblem(problem)).toList();
        }
        return synced(open, remote);
    }

    private ProductionChange synced(ProductionChange stored) {
        return synced(List.of(stored), proTech.read(List.of(stored))).get(0);
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

    /**
     * Stores the newer state read from ProTech: the action of opening or listing the change, which records it as
     * {@code CHANGE_SYNCED}, or a step on the way to another action, which records it so only when that action then
     * fails, as its own row stands for what it stored when it succeeds. A change someone stored meanwhile is taken as
     * they stored it.
     */
    private ProductionChange saved(ProductionChange synced, ProductionChange stored) {
        AuditNotes.tookPlace(stored);
        ProductionChange saved;
        try {
            saved = changes.save(synced);
        } catch (IllegalStateException stale) {
            return changes.load(stored.id()).orElseThrow(() -> stale);
        }
        AuditNotes.storedOnTheWay(CHANGE_SYNCED, saved);
        return saved;
    }

    private void publish(ProductionChange claimed, ProductionChange current) {
        try {
            proTech.update(claimed);
        } catch (RuntimeException refused) {
            recorded(current.toBuilder().version(claimed.version()).build());
            throw refused;
        }
    }

    private ProductionChange recorded(ProductionChange change) {
        return savedOnLatest(change, latest -> change.toBuilder().version(latest.version()).build());
    }

    /** Stores what {@code update} makes of the change, or of the latest version when someone stored one meanwhile. */
    private ProductionChange savedOnLatest(ProductionChange change, UnaryOperator<ProductionChange> update) {
        try {
            return changes.save(update.apply(change));
        } catch (IllegalStateException stale) {
            return changes.save(update.apply(changes.load(change.id()).orElseThrow(() -> stale)));
        }
    }

    private ProductionChange verified(ProductionChange published, ProductionChange current, Instant now) {
        try {
            ProductionChange remote = proTech.read(List.of(published)).get(published.number());
            return remote == null ? published.unverified(current, missing(published))
                    : published.synced(remote, now);
        } catch (UncheckedIOException e) {
            return published.unverified(current, syncProblem(e));
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

    private static void requireBusinessService(ProductionChange stored, Long businessServiceId, String action) {
        if (businessServiceId == null) {
            throw InvalidRequestException.of("businessServiceId", "choose your business service");
        }
        if (stored.businessServiceId() == null) {
            throw new SecurityException("No business service owns " + stored.number()
                    + ", so nobody can " + action + " it in Beadle");
        }
        if (!stored.businessServiceId().equals(businessServiceId)) {
            throw new SecurityException(
                    "Only " + stored.businessServiceName() + " can " + action + " " + stored.number());
        }
    }

    private static String syncProblem(RuntimeException e) {
        String reason = e instanceof UncheckedIOException ? "ProTech could not be reached: " : "ProTech refused the read: ";
        return CS.appendIfMissing(reason + e.getMessage(), ".");
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
