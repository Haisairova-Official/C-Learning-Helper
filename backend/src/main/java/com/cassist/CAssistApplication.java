package com.cassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@SpringBootApplication
public class CAssistApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(CAssistApplication.class);
        String readyFile = System.getenv("CASSIST_READY_FILE");
        if (readyFile != null && !readyFile.isBlank()) {
            var ready = new ApplicationPidFileWriter(readyFile);
            ready.setTriggerEventType(ApplicationReadyEvent.class);
            app.addListeners(ready);
        }
        app.run(args);
    }
}
