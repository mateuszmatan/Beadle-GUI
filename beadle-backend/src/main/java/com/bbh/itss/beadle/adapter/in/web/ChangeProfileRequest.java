package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.domain.change.ChangeTemplate;
import com.bbh.itss.beadle.domain.change.TaskDetails;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The template and change tasks of a product as the domain records hold them: ChangeTemplate and TaskDetails check
 * every field themselves in the use case, in bytes as Oracle stores them, and answer under the same paths
 * ({@code template.timing.installationStart}, {@code tasks[0].description}).
 */
public record ChangeProfileRequest(Long version, @NotNull ChangeTemplate template,
                                   @NotNull List<@NotNull TaskDetails> tasks) {
}
