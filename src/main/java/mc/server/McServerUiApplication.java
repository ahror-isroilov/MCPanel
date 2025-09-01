package mc.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class McServerUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(McServerUiApplication.class, args);
    }

}
