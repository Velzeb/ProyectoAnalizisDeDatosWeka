package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/limpieza")
    public String limpieza() {
        return "limpieza";
    }

    @GetMapping("/result")
    public String result() {
        return "result";
    }
}
