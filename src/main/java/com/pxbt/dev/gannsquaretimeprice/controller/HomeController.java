package com.pxbt.dev.gannsquaretimeprice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }

    @GetMapping("/index.html")
    public String index() {
        return "forward:/index.html";
    }
}