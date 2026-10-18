package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.catalog.port.in.BusinessServicesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_DELETED;
import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_RENAMED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri;

@RestController
@RequestMapping({"/api/business-services", "/api/departments"})
@RequiredArgsConstructor
public class BusinessServiceController {

    private final BusinessServicesUseCase businessServices;

    @GetMapping
    public List<BusinessServiceResponse> list() {
        return businessServices.list().stream().map(BusinessServiceResponse::of).toList();
    }

    @PostMapping
    @AuditedRequest(value = BUSINESS_SERVICE_CREATED, objectKey = "name")
    public ResponseEntity<BusinessServiceResponse> create(@Valid @RequestBody BusinessServiceRequest request) {
        BusinessServiceResponse created = BusinessServiceResponse.of(businessServices.create(request.name()));
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @AuditedRequest(BUSINESS_SERVICE_RENAMED)
    public BusinessServiceResponse rename(@PathVariable long id, @Valid @RequestBody BusinessServiceRequest request) {
        return BusinessServiceResponse.of(businessServices.rename(id, request.version(), request.name()));
    }

    @DeleteMapping("/{id}")
    @AuditedRequest(BUSINESS_SERVICE_DELETED)
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        businessServices.delete(id);
    }
}
