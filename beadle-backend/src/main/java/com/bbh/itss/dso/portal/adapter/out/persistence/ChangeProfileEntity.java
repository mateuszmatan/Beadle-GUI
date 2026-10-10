package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.out.persistence.ChangeTemplateEmbeddable.PrivilegedUserEmbeddable;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "DSO_CHANGE_PROFILE")
@NoArgsConstructor(access = PROTECTED)
public class ChangeProfileEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long productId;

    private ChangeTemplateEmbeddable template;

    @ElementCollection
    @CollectionTable(name = "DSO_CHANGE_PROFILE_PRIVILEGED_USER", joinColumns = @JoinColumn(name = "PROFILE_ID"))
    @OrderColumn(name = "POSITION")
    private List<PrivilegedUserEmbeddable> privilegedUsers = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "DSO_CHANGE_PROFILE_TASK", joinColumns = @JoinColumn(name = "PROFILE_ID"))
    @OrderColumn(name = "POSITION")
    private List<TaskDetailsEmbeddable> tasks = new ArrayList<>();

    ChangeProfileEntity(long productId) {
        this.productId = productId;
    }

    void apply(ChangeProfile profile) {
        this.template = ChangeTemplateEmbeddable.of(profile.template());
        replace(privilegedUsers, ChangeTemplateEmbeddable.usersOf(profile.template()));
        replace(tasks, profile.tasks().stream().map(task -> map(task, TaskDetailsEmbeddable.class)).toList());
    }

    ChangeTemplate template() {
        return template.toDomain(privilegedUsers);
    }

    ChangeProfile toDomain() {
        return map(ChangeProfile.class, this);
    }
}
