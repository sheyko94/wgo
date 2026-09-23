package com.example.wgo.configuration;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApiDocumentationController {

    @GetMapping("/")
    public String swaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }
}
