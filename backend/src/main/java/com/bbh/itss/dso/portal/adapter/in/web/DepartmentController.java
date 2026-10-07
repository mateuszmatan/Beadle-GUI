package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView;
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentsUseCase departments;

    public DepartmentController(DepartmentsUseCase departments) {
        this.departments = departments;
    }

    @GetMapping
    public List<DepartmentView> list() {
        return departments.list();
    }

    @PostMapping
    public ResponseEntity<DepartmentView> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentView created = departments.create(request.name());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public DepartmentView rename(@PathVariable long id, @Valid @RequestBody DepartmentRequest request) {
        return departments.rename(id, request.version(), request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        departments.delete(id);
    }
}
