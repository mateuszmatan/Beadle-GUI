package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeOptions;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeOptionsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.LookupsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ProductionChangesUseCase;
import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary;
import com.bbh.itss.dso.portal.domain.change.JiraIssue;
import com.bbh.itss.dso.portal.domain.change.JiraVersion;
import com.bbh.itss.dso.portal.domain.change.Lookup;
import com.bbh.itss.dso.portal.domain.change.ProductionChange;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChangeController {

    private final ProductionChangesUseCase changes;
    private final ChangeProfilesUseCase profiles;
    private final ChangeOptionsUseCase options;
    private final LookupsUseCase lookups;

    @GetMapping("/changes")
    public List<ProductionChange> list(@RequestParam(required = false) Long departmentId) {
        return changes.list(departmentId);
    }

    @GetMapping("/changes/{id}")
    public ProductionChange get(@PathVariable long id) {
        return changes.get(id);
    }

    @PutMapping("/changes/{id}")
    public ProductionChange update(@PathVariable long id, @Valid @RequestBody ChangeEditRequest request) {
        return changes.update(id, request.toCommand());
    }

    @GetMapping("/changes/integrations")
    public ChangeIntegrations integrations() {
        return changes.integrations();
    }

    @GetMapping("/changes/options")
    public ChangeOptions options() {
        return options.options();
    }

    @GetMapping("/lookups/{kind}")
    public List<Lookup> lookup(@PathVariable String kind, @RequestParam(required = false) String q) {
        return lookups.find(kind, q);
    }

    @PostMapping("/changes/preview")
    public ProductionChange preview(@Valid @RequestBody ChangeRequest request) {
        return changes.preview(request.toCommand());
    }

    @PostMapping("/changes")
    public ResponseEntity<ProductionChange> raise(@Valid @RequestBody ChangeRequest request) {
        ProductionChange raised = changes.raise(request.toCommand());
        return created(fromCurrentRequest().path("/{id}").buildAndExpand(raised.id()).toUri()).body(raised);
    }

    @PostMapping("/changes/{id}/tasks")
    public ProductionChange createTasks(@PathVariable long id, @Valid @RequestBody ChangeTasksRequest request) {
        return changes.createTasks(id, request.toCommand());
    }

    @GetMapping("/products/{id}/jira/versions")
    public List<JiraVersion> versions(@PathVariable long id, @RequestParam(required = false) String project) {
        return changes.versions(id, project);
    }

    @GetMapping("/products/{id}/jira/epics")
    public List<JiraIssue> epics(@PathVariable long id, @RequestParam(defaultValue = "") String fixVersion,
                                 @RequestParam(required = false) String project) {
        return changes.epics(id, fixVersion, project);
    }

    @GetMapping("/products/{id}/jira/stories")
    public List<JiraIssue> stories(@PathVariable long id, @RequestParam(defaultValue = "") String fixVersion,
                                   @RequestParam(defaultValue = "") List<String> epics,
                                   @RequestParam(required = false) String project) {
        return changes.stories(id, fixVersion, epics, project);
    }

    @GetMapping("/change-profiles")
    public List<ChangeProfileSummary> profiles() {
        return profiles.list();
    }

    @GetMapping("/products/{id}/change-profile")
    public ChangeProfileView profile(@PathVariable long id) {
        return profiles.get(id);
    }

    @PutMapping("/products/{id}/change-profile")
    public ChangeProfileView saveProfile(@PathVariable long id, @Valid @RequestBody ChangeProfileRequest request) {
        return profiles.save(id, request.version(), request.toTemplate(), request.toTasks());
    }
}
