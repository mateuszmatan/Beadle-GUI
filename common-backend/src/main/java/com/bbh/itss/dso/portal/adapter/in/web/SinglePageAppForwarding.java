package com.bbh.itss.dso.portal.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class SinglePageAppForwarding implements WebMvcConfigurer {

    static final String INDEX_PAGE = "forward:/index.html";

    private final List<String> pages;

    public SinglePageAppForwarding(@Value("${dso.gui.pages}") List<String> pages) {
        this.pages = List.copyOf(pages);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        pages.forEach(page -> List.of("/" + page, "/" + page + "/**")
                .forEach(path -> registry.addViewController(path).setViewName(INDEX_PAGE)));
    }
}
