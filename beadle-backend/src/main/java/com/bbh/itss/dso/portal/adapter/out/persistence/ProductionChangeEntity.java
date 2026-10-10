package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.out.persistence.ChangeTemplateEmbeddable.PrivilegedUserEmbeddable;
import com.bbh.itss.dso.portal.domain.change.ChangeState;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.EmbeddedColumnNaming;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_PRODUCTION_CHANGE")
@NoArgsConstructor(access = PROTECTED)
public class ProductionChangeEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(name = "CHANGE_NUMBER", updatable = false)
    private String number;

    private Long productId;
    private String productCode;
    private String productName;
    private Long departmentId;
    private String departmentName;
    private String openedBy;
    private String fixVersion;
    private ScheduleEmbeddable schedule;
    private String shortDescription;
    private String description;

    private ChangeTemplateEmbeddable template;

    @ElementCollection
    @CollectionTable(name = "DSO_PRODUCTION_CHANGE_PRIVILEGED_USER", joinColumns = @JoinColumn(name = "CHANGE_ID"))
    @OrderColumn(name = "POSITION")
    private List<PrivilegedUserEmbeddable> privilegedUsers = new ArrayList<>();

    private List<String> epicKeys;
    private List<String> storyKeys;
    private String url;

    @OneToMany(mappedBy = "change", cascade = ALL, orphanRemoval = true)
    @OrderBy("taskOrder")
    private List<ProductionChangeTaskEntity> tasks = new ArrayList<>();

    @Enumerated(STRING)
    private ChangeState state;

    @ElementCollection
    @CollectionTable(name = "DSO_PRODUCTION_CHANGE_STAGE", joinColumns = @JoinColumn(name = "CHANGE_ID"))
    @OrderColumn(name = "POSITION")
    private List<StageEmbeddable> workflow = new ArrayList<>();

    private Instant syncedAt;

    @EmbeddedColumnNaming("UPDATE_%s")
    private UpdateEmbeddable update;

    private long editedVersion;

    ProductionChangeEntity(ProductionChange change) {
        number = change.number();
        createdAt(change.createdAt());
        apply(change);
    }

    ProductionChangeEntity update(ProductionChange change) {
        if (!change.unappliedIn(toDomain()).isEmpty()) {
            editedVersion = change.editedVersion() < editedVersion ? change.editedVersion() : version() + 1;
        }
        touch();
        return apply(change);
    }

    private ProductionChangeEntity apply(ProductionChange change) {
        productId = change.productId();
        productCode = change.productCode();
        productName = change.productName();
        departmentId = change.departmentId();
        departmentName = change.departmentName();
        openedBy = change.openedBy();
        fixVersion = change.fixVersion();
        schedule = map(change.schedule(), ScheduleEmbeddable.class);
        shortDescription = change.shortDescription();
        description = change.description();
        template = ChangeTemplateEmbeddable.of(change.template());
        replace(privilegedUsers, ChangeTemplateEmbeddable.usersOf(change.template()));
        epicKeys = change.epicKeys();
        storyKeys = change.storyKeys();
        url = change.url();
        state = change.state();
        replace(workflow, change.workflow().stream().map(step -> map(step, StageEmbeddable.class)).toList());
        syncedAt = change.syncedAt();
        update = map(change.update(), UpdateEmbeddable.class);
        replaceTasks(change.tasks());
        return this;
    }

    ChangeTemplate template() {
        return template.toDomain(privilegedUsers);
    }

    ChangeUpdate update() {
        return update == null || update.status() == null ? null : map(update, ChangeUpdate.class);
    }

    ProductionChange toDomain() {
        return map(ProductionChange.class, this);
    }

    private void replaceTasks(List<ChangeTask> changed) {
        Map<String, ProductionChangeTaskEntity> stored = tasks.stream().filter(task -> task.number() != null)
                .collect(toMap(ProductionChangeTaskEntity::number, identity()));
        List<ProductionChangeTaskEntity> next = new ArrayList<>();
        for (int order = 0; order < changed.size(); order++) {
            ChangeTask task = changed.get(order);
            ProductionChangeTaskEntity kept = stored.get(task.number());
            next.add(kept == null ? new ProductionChangeTaskEntity(this, order, task) : kept.apply(order, task));
        }
        tasks.clear();
        tasks.addAll(next);
    }

    @Embeddable
    public record ScheduleEmbeddable(Instant installationStart, Instant installationEnd, Instant validationStart,
                                     Instant validationEnd, Instant firstUsage, Instant downtimeStart,
                                     Instant downtimeEnd) {
    }

    @Embeddable
    public record StageEmbeddable(@Enumerated(STRING) @Column(name = "STAGE") ChangeState state, Instant enteredAt) {
    }

    @Embeddable
    public record UpdateEmbeddable(@Enumerated(STRING) ChangeUpdate.Status status, Instant requestedAt,
                                   @Column(name = "DEPARTMENT") String departmentName, List<String> fields,
                                   String message, Instant checkedAt) {
    }
}
