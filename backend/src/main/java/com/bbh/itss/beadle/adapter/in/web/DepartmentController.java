package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.catalog.port.in.DepartmentsUseCase;
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

import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_DELETED;
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_RENAMED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentsUseCase departments;

    @GetMapping
    public List<DepartmentResponse> list() {
        return departments.list().stream().map(DepartmentResponse::of).toList();
    }

    @PostMapping
    @AuditedRequest(value = DEPARTMENT_CREATED, objectKey = "name")
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse created = DepartmentResponse.of(departments.create(request.name()));
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @AuditedRequest(DEPARTMENT_RENAMED)
    public DepartmentResponse rename(@PathVariable long id, @Valid @RequestBody DepartmentRequest request) {
        return DepartmentResponse.of(departments.rename(id, request.version(), request.name()));
    }

    @DeleteMapping("/{id}")
    @AuditedRequest(DEPARTMENT_DELETED)
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        departments.delete(id);
    }
}
