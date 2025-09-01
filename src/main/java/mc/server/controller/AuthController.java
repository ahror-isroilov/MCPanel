package mc.server.controller;

import lombok.RequiredArgsConstructor;
import mc.server.config.InitialSetupRunner;
import mc.server.model.User;
import mc.server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/setup")
    public String setup(@RequestParam String username, @RequestParam String password) {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
            InitialSetupRunner.setSetupRequired(false);
        }
        return "redirect:/login";
    }
}
