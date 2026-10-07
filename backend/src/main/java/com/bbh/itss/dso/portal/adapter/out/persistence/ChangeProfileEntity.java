package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.adapter.out.persistence.ChangeTemplateEmbeddable.PrivilegedUserEmbeddable;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "DSO_CHANGE_PROFILE")
public class ChangeProfileEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long productId;

    private ChangeTemplateEmbeddable template;

    @ElementCollection
    @CollectionTable(name = "DSO_CHANGE_PROFILE_PRIVILEGED_USER", joinColumns = @JoinColumn(name = "PROFILE_ID"))
    @OrderColumn(name = "POSITION")
    private List<PrivilegedUserEmbeddable> privilegedUsers = new ArrayList<>();

    protected ChangeProfileEntity() {
    }

    ChangeProfileEntity(long productId) {
        this.productId = productId;
    }

    void apply(ChangeTemplate template) {
        this.template = ChangeTemplateEmbeddable.of(template);
        List<PrivilegedUserEmbeddable> users = ChangeTemplateEmbeddable.usersOf(template);
        if (!privilegedUsers.equals(users)) {
            privilegedUsers.clear();
            privilegedUsers.addAll(users);
        }
    }

    ChangeTemplate template() {
        return template.toDomain(privilegedUsers);
    }

    ChangeProfile toDomain() {
        return RecordMapper.map(ChangeProfile.class, this);
    }
}
