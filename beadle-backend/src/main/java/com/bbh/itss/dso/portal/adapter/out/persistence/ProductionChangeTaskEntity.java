package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.out.persistence.ProductionChangeEntity.ReminderEmbeddable;
import com.bbh.itss.dso.portal.domain.change.ApprovalState;
import com.bbh.itss.dso.portal.domain.change.ChangeTask;
import com.bbh.itss.dso.portal.domain.change.TaskState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.EmbeddedColumnNaming;

import java.time.Instant;
import java.util.List;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
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
    @Getter(PACKAGE)
    private String number;

    private TaskDetailsEmbeddable details;

    @Column(name = "TASK_START")
    private Instant start;

    @Enumerated(STRING)
    private ApprovalState approval;

    private List<String> approvers;

    @EmbeddedColumnNaming("REMINDER_%s")
    private ReminderEmbeddable reminder;

    @Enumerated(STRING)
    private TaskState state;

    ProductionChangeTaskEntity(ProductionChangeEntity change, int taskOrder, ChangeTask task) {
        this.change = change;
        apply(taskOrder, task);
    }

    ProductionChangeTaskEntity apply(int taskOrder, ChangeTask task) {
        this.taskOrder = taskOrder;
        this.number = task.number();
        this.details = map(task.details(), TaskDetailsEmbeddable.class);
        this.start = task.start();
        this.approval = task.approval();
        this.approvers = task.approvers();
        this.reminder = map(task.reminder(), ReminderEmbeddable.class);
        this.state = task.state();
        return this;
    }
}
