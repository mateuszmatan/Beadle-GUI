package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionOutcome;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import com.bbh.itss.beadle.domain.audit.ObjectType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

/** A row of the audit trail. Immutable: Beadle inserts rows and never updates or deletes one. */
@Entity
@Immutable
@Table(name = "BEADLE_ACTION_HISTORY")
@NoArgsConstructor(access = PROTECTED)
public class ActionHistoryEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    private Instant at;

    private String userName;

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
        ActionHistoryEntity entity = new ActionHistoryEntity();
        entity.at = record.at();
        entity.userName = record.user();
        entity.action = record.action();
        entity.objectType = record.objectType();
        entity.objectId = record.objectId();
        entity.objectKey = record.objectKey();
        entity.details = record.details();
        entity.outcome = record.outcome();
        entity.message = record.message();
        entity.correlationId = record.correlationId();
        entity.durationMs = record.durationMs();
        return entity;
    }

    ActionRecord toDomain() {
        return new ActionRecord(id, at, userName, action, objectType, objectId, objectKey, details, outcome, message,
                correlationId, durationMs);
    }
}
