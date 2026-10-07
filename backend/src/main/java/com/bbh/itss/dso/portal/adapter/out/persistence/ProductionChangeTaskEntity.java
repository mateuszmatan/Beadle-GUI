package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_PRODUCTION_CHANGE_TASK")
@NoArgsConstructor(access = PROTECTED)
public class ProductionChangeTaskEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "CHANGE_ID")
    private ProductionChangeEntity change;

    private int taskOrder;

    @Column(name = "TASK_NUMBER")
    private String number;

    private String serviceName;
    private String shortDescription;
    private String description;

    ProductionChangeTaskEntity(ProductionChangeEntity change, int taskOrder, ChangeTask task) {
        this.change = change;
        this.taskOrder = taskOrder;
        this.number = task.number();
        this.serviceName = task.serviceName();
        this.shortDescription = task.shortDescription();
        this.description = task.description();
    }
}
