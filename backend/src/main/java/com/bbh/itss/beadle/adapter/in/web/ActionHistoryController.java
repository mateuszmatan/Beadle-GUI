package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase;
import com.bbh.itss.beadle.domain.audit.Action;
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage;
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery;
import com.bbh.itss.beadle.domain.audit.ActionOutcome;
import com.bbh.itss.beadle.domain.audit.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** The audit trail, newest first: what people and the two other Beadle services did in Beadle. */
@RestController
@RequestMapping("/api/action-history")
@RequiredArgsConstructor
public class ActionHistoryController {

    private final ActionHistoryUseCase history;

    @GetMapping
    public ActionHistoryPage find(@RequestParam(required = false) String user,
                                  @RequestParam(required = false) Action action,
                                  @RequestParam(required = false) ObjectType objectType,
                                  @RequestParam(required = false) String objectKey,
                                  @RequestParam(required = false) ActionOutcome outcome,
                                  @RequestParam(required = false) Instant from,
                                  @RequestParam(required = false) Instant to,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        return history.find(ActionHistoryQuery.of(user, action, objectType, objectKey, outcome, from, to, page, size));
    }

    @GetMapping("/users")
    public List<String> users() {
        return history.users();
    }

    @GetMapping("/actions")
    public List<Action> actions() {
        return history.actions();
    }
}
