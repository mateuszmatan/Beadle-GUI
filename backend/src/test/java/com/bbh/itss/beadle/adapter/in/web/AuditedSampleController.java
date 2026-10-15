package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.shared.InvalidRequestException;
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.util.List;

import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH;
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_RAISED;
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_RENAMED;
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT;

/** Endpoints of every kind of {@link AuditedRequest}, for the specification of {@link RefusedRequests}. */
@RestController
public class AuditedSampleController {

    @PostMapping("/api/samples/named")
    @AuditedRequest(value = DEPARTMENT_CREATED, objectKey = "name")
    public String create(@Valid @RequestBody Named request) {
        return request.name();
    }

    @PutMapping("/api/samples/named/{id}")
    @AuditedRequest(DEPARTMENT_RENAMED)
    public String rename(@PathVariable long id, @Valid @RequestBody Named request) {
        return id + " " + request.name();
    }

    @PostMapping("/api/samples/raised")
    @AuditedRequest(value = CHANGE_RAISED, objectId = "productId", objectType = PRODUCT)
    public String raise(@Valid @RequestBody Raised request) {
        return request.fixVersion();
    }

    @PostMapping("/api/samples/callback")
    @AuditedRequest(value = CALLBACK_PROTECH, objectKey = "number", actor = "system:protech")
    public String callback(@Valid @RequestBody Named request) {
        return request.name();
    }

    @PostMapping("/api/samples/refusing")
    @AuditedRequest(DEPARTMENT_CREATED)
    public String refusing(@Valid @RequestBody Named request) {
        throw new InvalidRequestException(List.of(new FieldProblem("name", "is taken")));
    }

    @PostMapping("/api/samples/unaudited")
    public String unaudited(@Valid @RequestBody Named request) {
        return request.name();
    }

    public record Named(@NotBlank @Size(max = 10) String name, String apiToken, DayOfWeek day, String number) {
    }

    public record Raised(@NotNull Long productId, @NotBlank String fixVersion) {
    }
}
