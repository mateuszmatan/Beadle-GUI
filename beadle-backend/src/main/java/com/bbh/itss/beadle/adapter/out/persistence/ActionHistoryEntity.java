package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionOutcome;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import com.bbh.itss.beadle.domain.audit.ObjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

import static com.bbh.itss.beadle.adapter.RecordMapper.map;
import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

/** A row of the audit trail. Immutable: Beadle inserts rows and never updates or deletes one. */
@Entity
@Immutable
@Table(name = "BEADLE_ACTION_HISTORY")
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor(access = PRIVATE)
public class ActionHistoryEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Instant at;

    @Column(name = "USER_NAME")
    private String user;

    @Enumerated(STRING)
    private Action action;

    @Enumerated(STRING)
    private ObjectType objectType;

    private Long objectId;

    private String objectKey;

    @Lob
    private String details;

    @Enumerated(STRING)
    private ActionOutcome outcome;

    private String message;

    private String correlationId;

    private long durationMs;

    static ActionHistoryEntity of(ActionRecord record) {
        return new ActionHistoryEntity(null, record.at(), record.user(), record.action(), record.objectType(),
                record.objectId(), record.objectKey(), record.details(), record.outcome(), record.message(),
                record.correlationId(), record.durationMs());
    }

    ActionRecord toDomain() {
        return map(ActionRecord.class, this);
    }
}
