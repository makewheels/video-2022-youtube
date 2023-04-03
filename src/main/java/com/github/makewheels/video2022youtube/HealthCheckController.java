package com.github.makewheels.video2022youtube;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HealthCheckController {
    @GetMapping("healthCheck")
    public String healthCheck() {
        log.info("healthCheck" + System.currentTimeMillis());
        return "ok " + System.currentTimeMillis();
    }
}
