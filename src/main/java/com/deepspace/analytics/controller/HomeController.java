package com.deepspace.analytics.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MVC controller for the home page of DeepSpace Analytics.
 */
@Controller
public class HomeController {

    private static final String PROJECT_TITLE =
            "Deep-Space Communication Using Poisson and Geometric Models for Counting Transmission Drops and Identifying the First Successful Transmission";

    /**
     * Renders the application home screen presenting the project overview and
     * the two core probability models (Poisson and Geometric distributions).
     *
     * @param model Thymeleaf view model
     * @return the index template name
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("activePage", "home");
        model.addAttribute("projectTitle", PROJECT_TITLE);
        return "index";
    }
}

