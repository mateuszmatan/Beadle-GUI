package com.bbh.itss.beadle.application.change.port.in;

import com.bbh.itss.beadle.domain.change.JiraIssue;
import com.bbh.itss.beadle.domain.change.JiraVersion;
import com.bbh.itss.beadle.domain.change.ProductionChange;

import java.util.List;
import java.util.Optional;

public interface ProductionChangesUseCase {

    List<ProductionChange> list(Long businessServiceId);

    ProductionChange get(long id);

    /** Reads the change of the given number again from ProTech; empty when Beadle did not create it. */
    Optional<ProductionChange> resync(String number);

    ProductionChange update(long id, ChangeEditCommand command);

    ChangeIntegrations integrations();

    List<JiraVersion> versions(long productId, String project);

    List<JiraIssue> epics(long productId, String fixVersion, String project);

    List<JiraIssue> stories(long productId, String fixVersion, List<String> epicKeys, String project);

    ProductionChange preview(ChangeCommand command);

    ProductionChange create(ChangeCommand command);

    ProductionChange createTasks(long id, ChangeTasksCommand command);

    ProductionChange createSecureCodingTicket(long id, SecureCodingCommand command);

    ProductionChange remind(long id, ReminderCommand command);
}
