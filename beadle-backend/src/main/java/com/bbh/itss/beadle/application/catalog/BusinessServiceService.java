package com.bbh.itss.beadle.application.catalog;

import com.bbh.itss.beadle.application.Audited;
import com.bbh.itss.beadle.application.AuditedKey;
import com.bbh.itss.beadle.application.ReadOnly;
import com.bbh.itss.beadle.application.UseCase;
import com.bbh.itss.beadle.application.catalog.port.in.BusinessServiceView;
import com.bbh.itss.beadle.application.catalog.port.in.BusinessServicesUseCase;
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceRepositoryPort;
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceUsagePort;
import com.bbh.itss.beadle.domain.catalog.BusinessService;
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_CREATED;
import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_DELETED;
import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_RENAMED;
import static com.bbh.itss.beadle.domain.shared.Failures.notFound;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparing;

@UseCase
@RequiredArgsConstructor
public class BusinessServiceService implements BusinessServicesUseCase {

    private final BusinessServiceRepositoryPort businessServices;
    private final BusinessServiceUsagePort usage;

    @Override
    @ReadOnly
    public List<BusinessServiceView> list() {
        Map<Long, BusinessServiceUsage> perBusinessService = usage.perBusinessService();
        return businessServices.findAll().stream()
                .sorted(comparing(BusinessService::name, CASE_INSENSITIVE_ORDER))
                .map(service -> new BusinessServiceView(service.id(), service.name(), service.version(),
                        perBusinessService.getOrDefault(service.id(), usage.unused())))
                .toList();
    }

    @Override
    @Audited(BUSINESS_SERVICE_CREATED)
    public BusinessServiceView create(@AuditedKey String name) {
        return view(businessServices.save(BusinessService.create(name, businessServices::findByName)).id());
    }

    @Override
    @Audited(BUSINESS_SERVICE_RENAMED)
    public BusinessServiceView rename(long id, Long version, String name) {
        BusinessService service = businessServices.load(id).orElseThrow(() -> notFound("BusinessService", id));
        businessServices.save(service.rename(version, name, businessServices::findByName));
        return view(id);
    }

    @Override
    @Audited(BUSINESS_SERVICE_DELETED)
    public void delete(long id) {
        BusinessServiceView service = view(id);
        long products = service.usage().productCount();
        if (products > 0) {
            throw new IllegalStateException(service.name() + " still has " + products
                    + " product(s). Move them to another business service first.");
        }
        service.usage().deletionRefusal(service.name()).ifPresent(refusal -> {
            throw new IllegalStateException(refusal);
        });
        businessServices.delete(id);
    }

    private BusinessServiceView view(long id) {
        return list().stream().filter(service -> service.id() == id).findFirst()
                .orElseThrow(() -> notFound("BusinessService", id));
    }
}
