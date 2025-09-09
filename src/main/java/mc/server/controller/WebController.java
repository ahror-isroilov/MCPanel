package mc.server.controller;

import lombok.RequiredArgsConstructor;
import mc.server.config.InitialSetupRunner;
import mc.server.repository.ServerInstanceRepository;
import mc.server.service.server.MinecraftServerService;
import mc.server.service.SystemMonitoringService;
import mc.server.service.TemplateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.security.Principal;
@Controller
@RequiredArgsConstructor
public class WebController {
    private final MinecraftServerService minecraftServerService;
    private final SystemMonitoringService systemMonitoringService;
    private final ServerInstanceRepository serverInstanceRepository;
    private final TemplateService templateService;

    @GetMapping("/")
    public String index() {
        return "redirect:/servers";
    }

    @GetMapping("/servers")
    public String servers(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        model.addAttribute("servers", serverInstanceRepository.findAll());
        model.addAttribute("breadcrumbs", new java.util.LinkedHashMap<String, String>() {{
            put("Servers", null);
        }});
        return "servers";
    }

    @GetMapping("/servers/{instanceId}/console")
    public String console(@PathVariable Long instanceId, Model model) {
        serverInstanceRepository.findById(instanceId).ifPresent(instance -> {
            model.addAttribute("instanceId", instanceId);
            model.addAttribute("instanceName", instance.getName());
            model.addAttribute("instanceVersion", instance.getVersion());
            model.addAttribute("serverStatus", minecraftServerService.getServerStatus(instanceId));
            model.addAttribute("breadcrumbs", new java.util.LinkedHashMap<String, String>() {{
                put("Servers", "/servers");
                put(instance.getName(), "/servers/" + instanceId + "/server");
                put("Console", null);
            }});
        });
        return "console";
    }

    @GetMapping("/servers/{instanceId}/server")
    public String server(@PathVariable Long instanceId, Model model) {
        serverInstanceRepository.findById(instanceId).ifPresent(instance -> {
            model.addAttribute("instance", instance);
            model.addAttribute("serverStatus", minecraftServerService.getServerStatus(instanceId));
            model.addAttribute("breadcrumbs", new java.util.LinkedHashMap<String, String>() {{
                put("Servers", "/servers");
                put(instance.getName(), null);
            }});
        });
        return "server";
    }

    @GetMapping("/system")
    public String system(Model model) {
        try {
            model.addAttribute("systemStats", systemMonitoringService.getSystemStats());
            model.addAttribute("jvmStats", systemMonitoringService.getJvmStats());
            model.addAttribute("breadcrumbs", new java.util.LinkedHashMap<String, String>() {{
                put("Servers", "/servers");
                put("System", null);
            }});
        } catch (Exception e) {
            // Handle error
        }
        return "system";
    }

    @GetMapping("/login")
    public String login() {
        if (InitialSetupRunner.isSetupRequired()) {
            return "redirect:/setup";
        }
        return "login";
    }

    @GetMapping("/setup")
    public String setup() {
        if (InitialSetupRunner.isSetupRequired()) {
            return "setup";
        }
        return "redirect:/login";
    }

    @GetMapping("/servers/create")
    public String createServerForm(Model model) {
        model.addAttribute("templates", templateService.getTemplates());
        model.addAttribute("breadcrumbs", new java.util.LinkedHashMap<String, String>() {{
            put("Servers", "/servers");
            put("Add Server", null);
        }});
        return "create-server";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("breadcrumbs", new java.util.LinkedHashMap<String, String>() {{
            put("Servers", "/servers");
            put("Settings", null);
        }});
        return "settings";
    }
}