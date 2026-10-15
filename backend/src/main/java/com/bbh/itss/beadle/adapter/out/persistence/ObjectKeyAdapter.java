package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort;
import com.bbh.itss.beadle.domain.audit.ObjectType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
class ObjectKeyAdapter implements ObjectKeyPort {

    private final EntityManager entities;

    @Override
    public Optional<String> keyOf(ObjectType type, long id) {
        String query = switch (type) {
            case CHANGE -> "select c.number from ProductionChangeEntity c where c.id = :id";
            case PRODUCT, CHANGE_PROFILE -> "select p.code from ProductEntity p where p.id = :id";
            case DEPARTMENT -> "select d.name from DepartmentEntity d where d.id = :id";
            case TICKET, USER -> null;
        };
        return query == null ? Optional.empty() : entities.createQuery(query, String.class).setParameter("id", id)
                .getResultStream().findFirst();
    }
}
