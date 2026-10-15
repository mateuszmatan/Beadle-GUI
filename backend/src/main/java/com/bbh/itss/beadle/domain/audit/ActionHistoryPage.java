package com.bbh.itss.beadle.domain.audit;

import java.util.List;

/** One page of the audit trail, newest first, with the number of rows all pages hold. */
public record ActionHistoryPage(List<ActionRecord> content, int page, int size, long total) {
}
