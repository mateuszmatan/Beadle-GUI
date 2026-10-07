package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviate;
import static com.bbh.itss.dso.portal.domain.shared.Text.orDefault;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
public class DemoChangeProfiles {

    static final String PRIVILEGED_PRODUCT = "PAYHUB";
    static final List<PrivilegedUser> PRIVILEGED_USERS = List.of(
            new PrivilegedUser("Priya Natarajan", "adm_pnatarajan"), new PrivilegedUser("Marcus Webb", "adm_mwebb"));
    static final List<String> LEVELS = List.of("Low", "Medium", "High");

    private static final Logger log = LoggerFactory.getLogger(DemoChangeProfiles.class);
    private static final List<String> MANAGERS = List.of("Olivia Bennett", "James Carter", "Sophia Turner",
            "William Hayes", "Emma Brooks", "Henry Collins", "Grace Mitchell", "Daniel Foster", "Charlotte Reed",
            "Samuel Price");
    private static final List<String> BUSINESS_APPROVERS = List.of("Rebecca Lawson", "Michael Grant",
            "Hannah Whitfield", "Robert Ellison", "Laura Kingsley", "Thomas Ashby");
    private static final List<String> START_TIMES = List.of("18:00", "19:00", "20:00");
    private static final List<String> AFFECTED_CLIENTS = List.of(
            "None: the release changes internal screens and batch jobs only.",
            "Fund administration clients see the new features after the release.",
            "Every client using the BBH client portal while the change is installed.");
    private static final List<String> BACKOUT_TESTING = List.of(
            "Rolled back to the previous release on QC with the deployment job, about 10 minutes.",
            "Rollback rehearsed on QC, including the database scripts, about 30 minutes.",
            "Full rollback rehearsed on QC with operations, about 60 minutes including the smoke tests.");

    private final ProductsUseCase products;
    private final ChangeProfilesUseCase profiles;

    public DemoChangeProfiles(ProductsUseCase products, ChangeProfilesUseCase profiles) {
        this.products = products;
        this.profiles = profiles;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fillIn() {
        int filled = 0;
        for (ProductSummaryView product : products.list(null)) {
            ChangeProfileView profile = profiles.get(product.id());
            if (profile.version() == null) {
                profiles.save(product.id(), null, defaultsFor(product, profile.template()));
                filled++;
            }
        }
        log.info("Filled in the demo ServiceNow change template of {} product(s)", filled);
    }

    static ChangeTemplate defaultsFor(ProductSummaryView product, ChangeTemplate suggested) {
        Random random = new Random(product.code().hashCode());
        int level = random.nextInt(LEVELS.size());
        List<String> managers = random.ints(0, MANAGERS.size()).distinct().limit(2).mapToObj(MANAGERS::get).toList();
        Approvers approvers = new Approvers(managers.get(0), managers.get(1),
                BUSINESS_APPROVERS.get(random.nextInt(BUSINESS_APPROVERS.size())));
        Timing timing = new Timing(START_TIMES.get(random.nextInt(START_TIMES.size())), 2 + level, 1);
        PrivilegedAccess access = PRIVILEGED_PRODUCT.equals(product.code())
                ? new PrivilegedAccess(true, PRIVILEGED_USERS) : PrivilegedAccess.NONE;
        RiskAssessment risk = new RiskAssessment(1 + random.nextInt(2 + 2 * level),
                List.of(25, 150, 800).get(level) + random.nextInt(50), 1 + random.nextInt(1 + 2 * level),
                level == 0 ? 0 : 5 * level + random.nextInt(20 * level), level == 2 ? random.nextInt(4) : 0,
                LEVELS.get(level), LEVELS.get(random.nextInt(level + 1)), LEVELS.get(random.nextInt(level + 1)),
                BACKOUT_TESTING.get(level), level == 2 ? "Platform upgrade" : "Existing platform");
        String group = abbreviate(orDefault(product.ownerTeam(), product.name()) + " Application Support", GROUP_MAX);
        return new ChangeTemplate(suggested.jiraProjectKey(), group, suggested.category(), suggested.type(),
                suggested.configurationItem(), null, null, null, AFFECTED_CLIENTS.get(level), suggested.description(),
                approvers, level == 2, timing, suggested.planning(), access, risk);
    }
}
