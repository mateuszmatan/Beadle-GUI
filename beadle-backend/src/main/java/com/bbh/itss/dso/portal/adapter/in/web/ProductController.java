package com.bbh.itss.dso.portal.adapter.in.web;

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductCommand;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductView;
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase;
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

import static com.bbh.itss.dso.portal.adapter.RecordMapper.map;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.ResponseEntity.created;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequestUri;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductsUseCase products;

    @GetMapping
    public List<ProductView> list(@RequestParam(required = false) String search) {
        return products.list(search);
    }

    @GetMapping("/code-suggestion")
    public CodeSuggestion suggestCode(@RequestParam String name) {
        return new CodeSuggestion(products.suggestCode(name));
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable long id) {
        return products.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductView> create(@Valid @RequestBody ProductRequest request) {
        ProductView created = products.create(map(request, ProductCommand.class));
        return created(fromCurrentRequestUri().path("/{id}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    public ProductView update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, map(request, ProductCommand.class));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(NO_CONTENT)
    public void delete(@PathVariable long id) {
        products.delete(id);
    }
}
