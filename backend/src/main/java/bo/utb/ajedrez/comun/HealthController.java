package bo.utb.ajedrez.comun;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    @SecurityRequirements
    public Map<String, String> health() {
        return Map.of("estado", "ok");
    }
}
