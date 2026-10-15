package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort;
import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;
import com.bbh.itss.beadle.domain.audit.ActionRecord;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.util.Locale.ROOT;
import static org.springframework.data.domain.Sort.Direction.DESC;

@Component
@RequiredArgsConstructor
class ActionHistoryPersistenceAdapter implements ActionHistoryPort {

    static final Sort NEWEST_FIRST = Sort.by(DESC, "at", "id");

    private final ActionHistoryJpaRepository rows;

    @Override
    public ActionRecord record(ActionRecord record) {
        return rows.save(ActionHistoryEntity.of(record)).toDomain();
    }

    @Override
    public ActionHistoryPage find(ActionHistoryQuery query) {
        Page<ActionHistoryEntity> page = rows.findAll(matching(query),
                PageRequest.of(query.page(), query.size(), NEWEST_FIRST));
        return new ActionHistoryPage(page.getContent().stream().map(ActionHistoryEntity::toDomain).toList(),
                query.page(), query.size(), page.getTotalElements());
    }

    @Override
    public List<String> users() {
        return rows.findUserNames();
    }

    @Override
    public List<Action> actions() {
        return rows.findActions().stream().sorted(Comparator.comparing(Action::name)).toList();
    }

    static Specification<ActionHistoryEntity> matching(ActionHistoryQuery query) {
        return (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.user() != null) {
                predicates.add(builder.equal(root.get("userName"), query.user()));
            }
            if (query.action() != null) {
                predicates.add(builder.equal(root.get("action"), query.action()));
            }
            if (query.objectType() != null) {
                predicates.add(builder.equal(root.get("objectType"), query.objectType()));
            }
            if (query.objectKey() != null) {
                predicates.add(builder.like(builder.upper(root.get("objectKey")),
                        escaped(query.objectKey().toUpperCase(ROOT)) + "%", '\\'));
            }
            if (query.outcome() != null) {
                predicates.add(builder.equal(root.get("outcome"), query.outcome()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("at"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.get("at"), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escaped(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
