package com.bbh.itss.beadle.domain.change;

import java.time.Instant;

public record WorkflowStep(ChangeState state, Instant enteredAt) {
}
