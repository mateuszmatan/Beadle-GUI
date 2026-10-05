package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.adapter.RecordMapper;
import com.bbh.itss.dso.portal.application.catalog.port.in.ManageProductsUseCase;
import com.bbh.itss.dso.portal.application.catalog.port.in.QueryProductsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final QueryProductsUseCase queries;
    private final ManageProductsUseCase products;

    public ProductController(QueryProductsUseCase queries, ManageProductsUseCase products) {
        this.queries = queries;
        this.products = products;
    }

    @GetMapping
    public List<ProductSummaryResponse> list(@RequestParam(required = false) String search) {
        return queries.list(search).stream().map(view -> RecordMapper.map(view, ProductSummaryResponse.class)).toList();
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable long id) {
        return ProductDto.from(queries.get(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductDto request) {
        ProductDto created = ProductDto.from(products.create(request.toCommand()));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable long id, @Valid @RequestBody ProductDto request) {
        return ProductDto.from(products.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        products.delete(id);
    }

    public record ProductSummaryResponse(Long id, String code, String name, String description, String ownerTeam,
                                         long serviceCount, long pipelineCount, long activePipelineCount,
                                         Instant updatedAt) {
    }
}
