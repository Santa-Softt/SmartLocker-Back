package com.smartlockr.iam.infrastructure.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fast")
public class FastController {

    @GetMapping
    public Map<String,String> workingController(){
        return Map.of("status", "working");
    }
}
