package com.bbh.itss.dso.portal.adapter.in.startup;

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.Locale.ROOT;

@Component
@ConditionalOnBooleanProperty("dso.demo-data")
@RequiredArgsConstructor
@Slf4j
public class DemoProducts implements ApplicationRunner {

    static final List<DemoProduct> CATALOGUE = List.of(
            new DemoProduct("DOCSENSE", "DocSense", "AI Lab", "AI Lab Engineering"),
            new DemoProduct("ADVISORAI", "Advisor Assistant", "AI Lab", "AI Lab Engineering"),
            new DemoProduct("DEALFLOW", "DealFlow", "Capital Partners", "Private Equity Technology"),
            new DemoProduct("LPPORTAL", "LP Portal", "Capital Partners", "Investor Reporting"),
            new DemoProduct("ACCESSHUB", "Access Hub", "Corporate Technology", "Identity and Access"),
            new DemoProduct("CERTSCANNER", "CertScanner", "Corporate Technology", "Technology Architecture"),
            new DemoProduct("SAFEKEEP", "Safekeeping Ledger", "Custody", "Custody Platform"),
            new DemoProduct("CORPACT", "Corporate Actions", "Custody", "Asset Servicing"),
            new DemoProduct("PAYHUB", "Payments Hub", "Fund Services", "Payments Engineering"),
            new DemoProduct("NAVCALC", "NAV Calculator", "Fund Services", "Fund Accounting"));

    private final ProductsUseCase products;
    private final DepartmentsUseCase departments;

    @Override
    public void run(ApplicationArguments args) {
        if (!products.list(null).isEmpty()) {
            return;
        }
        CATALOGUE.forEach(demo -> products.create(new ProductCommand(demo.code(), demo.name(),
                department(demo.department()), demo.team(),
                demo.team().toLowerCase(ROOT).replaceAll("[^a-z]+", "-") + "@bbh.com", null)));
        log.info("Created the {} demo products", CATALOGUE.size());
    }

    private long department(String name) {
        return departments.list().stream().filter(department -> department.name().equalsIgnoreCase(name))
                .findFirst().orElseGet(() -> departments.create(name)).id();
    }

    record DemoProduct(String code, String name, String department, String team) {
    }
}
