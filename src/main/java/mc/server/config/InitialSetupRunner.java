package mc.server.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import mc.server.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialSetupRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    @Setter
    @Getter
    private static volatile boolean setupRequired = false;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            setupRequired = true;
        }
    }
}
