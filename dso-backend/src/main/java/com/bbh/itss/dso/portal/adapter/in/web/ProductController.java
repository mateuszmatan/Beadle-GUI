package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
import com.bbh.itss.dso.portal.domain.pipeline.PipelineType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductsUseCase products;

    @GetMapping
    public List<ProductSummaryView> list(@RequestParam(required = false) String search) {
        return products.list(search);
    }

    @GetMapping("/code-suggestion")
    public CodeSuggestion suggestCode(@RequestParam String name) {
        return new CodeSuggestion(products.suggestCode(name));
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable long id) {
        return ProductDto.from(products.get(id));
    }

    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductDto request,
                                             @RequestParam(required = false) PipelineType pipelineType) {
        ProductDto created = ProductDto.from(products.create(request.toCommand(pipelineType)));
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable long id, @Valid @RequestBody ProductDto request,
                             @RequestParam(required = false) PipelineType pipelineType) {
        return ProductDto.from(products.update(id, request.toCommand(pipelineType)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        products.delete(id);
    }
}
