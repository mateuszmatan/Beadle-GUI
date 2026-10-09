package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView;
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase;
import com.bbh.itss.dso.portal.application.change.port.in.LookupsUseCase;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser;
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing;
import com.bbh.itss.dso.portal.domain.change.Lookup;
import com.bbh.itss.dso.portal.domain.change.RiskAssessment;
import com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question;
import com.bbh.itss.dso.portal.domain.change.TaskDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.GROUP_MAX;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.LOW;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BACKOUT_TESTING;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BBH_APPLICATIONS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BBH_USERS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.BBH_WORKGROUPS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.CHANGE_COMPLEXITY;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.CLIENTS_OUTSIDE_BBH;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.PLATFORM_STATUS;
import static com.bbh.itss.dso.portal.domain.change.RiskAssessment.Question.VALIDATION_COMPLEXITY;
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.suggestedTasks;
import static com.bbh.itss.dso.portal.domain.shared.Text.abbreviateBytes;
import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.trim;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
@RequiredArgsConstructor
@Slf4j
public class DemoChangeProfiles {

    static final String PRIVILEGED_PRODUCT = "PAYHUB";
    static final String DATABASE_GROUP = "Database Administration";
    static final List<PrivilegedUser> PRIVILEGED_USERS = List.of(
            new PrivilegedUser("Priya Natarajan", "adm_pnatarajan"), new PrivilegedUser("Marcus Webb", "adm_mwebb"));
    static final List<String> IMPACTS = List.of("None", "Medium", "High");
    static final List<Integer> NAMED_CLIENTS = List.of(0, 1, 3, 5);

    private static final List<String> MANAGERS = List.of("Olivia Bennett", "James Carter", "Sophia Turner",
            "William Hayes", "Emma Brooks", "Henry Collins", "Grace Mitchell", "Daniel Foster", "Charlotte Reed",
            "Samuel Price");
    private static final List<String> BUSINESS_APPROVERS = List.of("Rebecca Lawson", "Michael Grant",
            "Hannah Whitfield", "Robert Ellison", "Laura Kingsley", "Thomas Ashby");
    private static final List<String> START_TIMES = List.of("18:00", "19:00", "20:00");
    private static final List<String> USERS_AFFECTED = List.of(
            "Internal users of %s only; no client sees the change.",
            "The operations users of %s and the clients it serves, during the change window.",
            "Every user of %s, inside BBH and at the clients, while the change is installed.");

    private final ProductsUseCase products;
    private final ChangeProfilesUseCase profiles;
    private final LookupsUseCase lookups;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void fillIn() {
        int filled = 0;
        for (ProductSummaryView product : products.list(null)) {
            ChangeProfileView profile = profiles.get(product.id());
            if (profile.version() == null) {
                ChangeTemplate defaults = defaultsFor(product, profile.template());
                profiles.save(product.id(), null, defaults, tasksFor(product, defaults));
                filled++;
            }
        }
        log.info("Filled in the demo ProTech change template of {} product(s)", filled);
    }

    static List<TaskDetails> tasksFor(ProductSummaryView product, ChangeTemplate defaults) {
        List<TaskDetails> tasks = new ArrayList<>(suggestedTasks(product.name(), defaults.assignmentGroup()));
        if (LOW.equals(defaults.risk())) {
            return tasks;
        }
        tasks.add(1, TaskDetails.builder().assignmentGroup(DATABASE_GROUP)
                .shortDescription("Run the database scripts of " + product.name())
                .description("Run the reviewed database scripts of the " + product.name() + " release on the"
                        + " production database before the deployment, then record the scripts and their result in"
                        + " this task.").build());
        return tasks;
    }

    ChangeTemplate defaultsFor(ProductSummaryView product, ChangeTemplate suggested) {
        Random random = new Random(product.code().hashCode());
        int level = random.nextInt(IMPACTS.size());
        List<String> managers = random.ints(0, MANAGERS.size()).distinct().limit(2).mapToObj(MANAGERS::get).toList();
        Approvers approvers = new Approvers(managers.get(0), managers.get(1),
                BUSINESS_APPROVERS.get(random.nextInt(BUSINESS_APPROVERS.size())));
        Timing timing = new Timing(START_TIMES.get(random.nextInt(START_TIMES.size())), 2 + level, 1);
        PrivilegedAccess access = PRIVILEGED_PRODUCT.equals(product.code())
                ? new PrivilegedAccess(true, PRIVILEGED_USERS) : PrivilegedAccess.NONE;
        String outside = answer(CLIENTS_OUTSIDE_BBH, level, random);
        List<String> clients = new ArrayList<>(lookups.find("clients", null).stream().map(Lookup::value).toList());
        shuffle(clients, random);
        int named = NAMED_CLIENTS.get(CLIENTS_OUTSIDE_BBH.options().indexOf(outside));
        RiskAssessment risk = RiskAssessment.builder().bbhWorkgroups(answer(BBH_WORKGROUPS, level, random))
                .changeComplexity(answer(CHANGE_COMPLEXITY, level, random)).bbhUsers(BBH_USERS.options().get(level))
                .validationComplexity(answer(VALIDATION_COMPLEXITY, level, random))
                .bbhApplications(answer(BBH_APPLICATIONS, level, random))
                .backoutTesting(BACKOUT_TESTING.options().get(level))
                .clientsOutsideBbh(outside)
                .platformStatus(PLATFORM_STATUS.options().get(level / 2)).businessImpact(IMPACTS.get(level)).build();
        String group = abbreviateBytes(defaultIfBlank(trim(product.ownerTeam()), product.name())
                + " Application Support", GROUP_MAX);
        return suggested.toBuilder().assignmentGroup(group)
                .directBusinessService(lookups.find("configuration-items", suggested.configurationItem()).stream()
                        .filter(item -> item.value().equals(suggested.configurationItem())).map(Lookup::detail)
                        .findFirst().orElse(null))
                .affectedClients(clients.stream().limit(named).collect(joining(", ")))
                .usersAffected(USERS_AFFECTED.get(level).formatted(product.name())).approvers(approvers)
                .downtime(level == 2).timing(timing).privilegedAccess(access).riskAssessment(risk)
                .secureCodingTicket("APPSEC-" + (1000 + random.nextInt(9000))).build();
    }

    private static String answer(Question question, int level, Random random) {
        int last = question.options().size() - 1;
        return question.options().get(level == 0 ? 0 : level == 1 ? random.nextInt(last) : 1 + random.nextInt(last));
    }
}
