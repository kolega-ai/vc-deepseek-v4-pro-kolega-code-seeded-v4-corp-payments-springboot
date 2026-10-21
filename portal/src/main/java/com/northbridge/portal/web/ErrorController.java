package com.northbridge.portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ErrorController {

    @GetMapping("/403")
    public String forbidden(Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("title", "Access denied");
        model.addAttribute("message", "Your role does not permit this action.");
        return "error";
    }

    @GetMapping("/404")
    public String notFound(Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("title", "Not found");
        model.addAttribute("message", "The record does not exist or belongs to a different company.");
        return "error";
    }
}
