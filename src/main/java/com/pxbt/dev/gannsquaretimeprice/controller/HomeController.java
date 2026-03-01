package com.pxbt.dev.gannsquaretimeprice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    @GetMapping("/")
    public String home() {
        log.info("🔥 Home controller / called");
        return "forward:/index.html";
    }

    @GetMapping("/index.html")
    public String index() {
        log.info("🔥 Home controller /index.html called");
        return "forward:/index.html";
    }
}