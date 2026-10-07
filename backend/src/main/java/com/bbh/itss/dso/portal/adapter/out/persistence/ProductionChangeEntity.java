package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.adapter.out.persistence.ChangeTemplateEmbeddable.PrivilegedUserEmbeddable;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.CascadeType.ALL;
import static jakarta.persistence.GenerationType.IDENTITY;
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
    private String departmentName;
    private String fixVersion;
    private ScheduleEmbeddable schedule;
    private String shortDescription;
    private String description;

    @AttributeOverride(name = "description", column = @Column(name = "PRODUCT_DESCRIPTION"))
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

    ProductionChangeEntity(ProductionChange change) {
        number = change.number();
        productId = change.productId();
        productCode = change.productCode();
        productName = change.productName();
        departmentName = change.departmentName();
        fixVersion = change.fixVersion();
        schedule = RecordMapper.map(change.schedule(), ScheduleEmbeddable.class);
        shortDescription = change.shortDescription();
        description = change.description();
        template = ChangeTemplateEmbeddable.of(change.template());
        privilegedUsers.addAll(ChangeTemplateEmbeddable.usersOf(change.template()));
        epicKeys = change.epicKeys();
        storyKeys = change.storyKeys();
        url = change.url();
        for (int order = 0; order < change.tasks().size(); order++) {
            tasks.add(new ProductionChangeTaskEntity(this, order, change.tasks().get(order)));
        }
    }

    ChangeTemplate template() {
        return template.toDomain(privilegedUsers);
    }

    ProductionChange toDomain() {
        return RecordMapper.map(ProductionChange.class, this);
    }

    @Embeddable
    public record ScheduleEmbeddable(Instant installationStart, Instant installationEnd, Instant validationStart,
                                     Instant validationEnd, Instant firstUsage) {
    }
}
