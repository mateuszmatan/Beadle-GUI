package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.dso.portal.domain.change.DateRange;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ChangeController {

    private final ProductionChangesUseCase changes;
    private final ChangeProfilesUseCase profiles;

    public ChangeController(ProductionChangesUseCase changes, ChangeProfilesUseCase profiles) {
        this.changes = changes;
        this.profiles = profiles;
    }

    @GetMapping("/changes")
    public List<ProductionChange> list() {
        return changes.list();
    }

    @GetMapping("/changes/{id}")
    public ProductionChange get(@PathVariable long id) {
        return changes.get(id);
    }

    @GetMapping("/changes/integrations")
    public ChangeIntegrations integrations() {
        return changes.integrations();
    }

    @PostMapping("/changes/preview")
    public ProductionChange preview(@Valid @RequestBody ChangeRequest request) {
        return changes.preview(request.toCommand());
    }

    @PostMapping("/changes")
    public ResponseEntity<ProductionChange> raise(@Valid @RequestBody ChangeRequest request) {
        ProductionChange raised = changes.raise(request.toCommand());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(raised.id()).toUri()).body(raised);
    }

    @GetMapping("/products/{id}/jira/epics")
    public List<JiraIssue> epics(@PathVariable long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return changes.epics(id, new DateRange(from, to));
    }

    @GetMapping("/products/{id}/jira/stories")
    public List<JiraIssue> stories(@PathVariable long id, @RequestParam(defaultValue = "") List<String> epics,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return changes.stories(id, epics, new DateRange(from, to));
    }

    @GetMapping("/products/{id}/change-profile")
    public ChangeProfileView profile(@PathVariable long id) {
        return profiles.get(id);
    }

    @PutMapping("/products/{id}/change-profile")
    public ChangeProfileView saveProfile(@PathVariable long id, @Valid @RequestBody ChangeProfileRequest request) {
        return profiles.save(id, request.version(), request.toTemplate());
    }
}
