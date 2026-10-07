package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Impact;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Risk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
@RequiredArgsConstructor
@Slf4j
public class DemoChangeProfiles {

    private static final List<String> MANAGERS = List.of("Olivia Bennett", "James Carter", "Sophia Turner",
            "William Hayes", "Emma Brooks", "Henry Collins", "Grace Mitchell", "Daniel Foster", "Charlotte Reed",
            "Samuel Price");
    private static final List<String> ASSESSMENTS = List.of(
            "Routine release of tested changes. Every service is deployed with its pipeline and can be rolled back"
                    + " to the previous release within minutes.",
            "Several services change together and clients see the new features right away. The release was"
                    + " rehearsed on QC and the rollback was tested.",
            "Changes the processing of client data during business hours. Operations monitor the first hour and"
                    + " the business owner signs off the smoke tests.");

    private final ProductsUseCase products;
    private final ChangeProfilesUseCase profiles;

    @EventListener(ApplicationReadyEvent.class)
    public void fillIn() {
        int filled = 0;
        for (ProductSummaryView product : products.list(null)) {
            ChangeProfileView profile = profiles.get(product.id());
            if (profile.version() == null) {
                Random random = new Random(product.code().hashCode());
                int level = random.nextInt(ASSESSMENTS.size());
                List<String> approvers = random.ints(0, MANAGERS.size()).distinct().limit(2 + level)
                        .mapToObj(MANAGERS::get).toList();
                profiles.save(product.id(), null, profile.template().assessed(Risk.values()[level],
                        Impact.values()[random.nextInt(level + 1)], ASSESSMENTS.get(level), approvers));
                filled++;
            }
        }
        log.info("Filled in the demo ServiceNow change template of {} product(s)", filled);
    }
}
