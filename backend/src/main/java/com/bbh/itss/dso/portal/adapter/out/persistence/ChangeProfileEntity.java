package com.bbh.itss.dso.portal.adapter.out.persistence;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.domain.change.ChangeProfile;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "DSO_CHANGE_PROFILE")
public class ChangeProfileEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long productId;

    private ChangeTemplateEmbeddable template;

    protected ChangeProfileEntity() {
    }

    ChangeProfileEntity(long productId) {
        this.productId = productId;
    }

    void apply(ChangeTemplate template) {
        this.template = new ChangeTemplateEmbeddable(template);
    }

    ChangeProfile toDomain() {
        return RecordMapper.map(ChangeProfile.class, this);
    }
}
