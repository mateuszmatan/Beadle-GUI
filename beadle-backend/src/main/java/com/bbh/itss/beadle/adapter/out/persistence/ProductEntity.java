package com.bbh.itss.beadle.adapter.out.persistence;

import com.bbh.itss.beadle.domain.catalog.Product;
import com.bbh.itss.beadle.domain.catalog.ProductDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "BEADLE_PRODUCT")
@NoArgsConstructor(access = PROTECTED)
public class ProductEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Getter(PACKAGE)
    private Long id;

    private String code;

    @Getter(PACKAGE)
    private String name;

    private String ownerTeam;
    private String contactEmail;

    @Column(name = "BUSINESS_SERVICE_ID")
    private Long businessServiceId;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "BUSINESS_SERVICE_ID", insertable = false, updatable = false)
    private BusinessServiceEntity businessService;

    Product toDomain() {
        return new Product(id, new ProductDetails(code, name, ownerTeam, contactEmail, businessServiceId), version(),
                updatedAt());
    }

    void apply(ProductDetails details) {
        code = details.code();
        name = details.name();
        ownerTeam = details.ownerTeam();
        contactEmail = details.contactEmail();
        businessServiceId = details.businessServiceId();
    }
}
