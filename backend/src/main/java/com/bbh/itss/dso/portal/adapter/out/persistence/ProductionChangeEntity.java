package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.change.ChangeWindow;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
    private Instant plannedStart;
    private Instant plannedEnd;
    private String shortDescription;
    private String description;

    @AttributeOverride(name = "description", column = @Column(name = "PRODUCT_DESCRIPTION"))
    private ChangeTemplateEmbeddable template;

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
        plannedStart = change.window().start();
        plannedEnd = change.window().end();
        shortDescription = change.shortDescription();
        description = change.description();
        template = new ChangeTemplateEmbeddable(change.template());
        epicKeys = change.epicKeys();
        storyKeys = change.storyKeys();
        url = change.url();
        for (int order = 0; order < change.tasks().size(); order++) {
            tasks.add(new ProductionChangeTaskEntity(this, order, change.tasks().get(order)));
        }
    }

    ChangeWindow window() {
        return new ChangeWindow(plannedStart, plannedEnd);
    }

    ProductionChange toDomain() {
        return RecordMapper.map(ProductionChange.class, this);
    }
}
