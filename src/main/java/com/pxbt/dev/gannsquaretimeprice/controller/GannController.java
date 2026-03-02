package com.pxbt.dev.gannsquaretimeprice.controller;

import com.pxbt.dev.gannsquaretimeprice.Service.GannSquareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
public class GannController {

    @Autowired
    private GannSquareService gannService;

    @GetMapping("/gann")
    public ResponseEntity<String> getGannAnalysis(
            @RequestParam(defaultValue = "BTC") String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "180") int limit) {

        String result = gannService.runFullAnalysis(symbol, interval, limit);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/klines")
    public ResponseEntity<String> getKlines(
            @RequestParam(defaultValue = "BTC") String symbol,
            @RequestParam(defaultValue = "1d") String interval,
            @RequestParam(defaultValue = "180") int limit) {

        String result = gannService.getRawKlines(symbol, interval, limit);
        return ResponseEntity.ok(result);
    }
}