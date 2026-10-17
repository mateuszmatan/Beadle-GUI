package com.bbh.itss.beadle.application.user.port.in;

import com.bbh.itss.beadle.domain.audit.ActionObject;
import com.bbh.itss.beadle.domain.audit.AuditedObject;

import java.util.List;

import static com.bbh.itss.beadle.domain.audit.ObjectType.USER;

/** The signed-in user as {@code GET /api/me} answers it; {@code email} is {@code null} when it is not known. */
public record SignedInUser(
        String name,
        String email,
        List<String> roles,
        List<String> departmentNames,
        boolean admin
) implements AuditedObject {

    public static final List<String> DEFAULT_DEPARTMENTS = List.of(
            "Corporate Technology",
            "Custody",
            "Fund Services",
            "Markets",
            "Investor Services"
    );

    public static final List<String> DEFAULT_ROLES = List.of(
            "ROLE_ADMIN",
            "ROLE_USER",
            "ROLE_DEPT_CORPORATE_TECHNOLOGY",
            "ROLE_DEPT_CUSTODY",
            "ROLE_DEPT_FUND_SERVICES",
            "ROLE_DEPT_MARKETS",
            "ROLE_DEPT_INVESTOR_SERVICES"
    );

    public SignedInUser(String name) {
        this(name, null, DEFAULT_ROLES, DEFAULT_DEPARTMENTS, true);
    }

    public SignedInUser(String name, String email) {
        this(name, email, DEFAULT_ROLES, DEFAULT_DEPARTMENTS, true);
    }

    public static SignedInUser defaultAdmin() {
        return new SignedInUser("Mateusz Matan", "mateusz.matan@bbh.com", DEFAULT_ROLES, DEFAULT_DEPARTMENTS, true);
    }

    @Override
    public ActionObject auditedObject() {
        return ActionObject.keyed(USER, name);
    }
}
