package mc.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mc.server.dto.ServerTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private List<ServerTemplate> templates;

    @PostConstruct
    public void loadTemplates() {
        try {
            Resource resource = resourceLoader.getResource("classpath:data/templates.json");
            try (InputStream inputStream = resource.getInputStream()) {
                templates = objectMapper.readValue(inputStream, new TypeReference<>() {});
                log.info("Loaded {} server templates from JSON.", templates.size());
            }
        } catch (IOException e) {
            log.error("Failed to load server templates from JSON.", e);
            templates = Collections.emptyList();
        }
    }

    public List<ServerTemplate> getTemplates() {
        return templates;
    }
}