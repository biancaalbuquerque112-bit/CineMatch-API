
package com.cinematch.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaudeController {

    @GetMapping("/api/saude")
    public String verificar() {
        return "CineMatch API funcionando!";
    }
}
