package dev.opsflow;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.*;
import static dev.opsflow.Models.*;

@RestController
@RequestMapping("/api/organizations/{tenant}")
class IncidentController {
    private final IncidentService service;
    IncidentController(IncidentService service) { this.service=service; }
    @GetMapping("/incidents")
    Page<Incident> list(@PathVariable UUID tenant,@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(tenant,jwt.getSubject(),status,page,size);
    }
    @GetMapping("/summary")
    Summary summary(@PathVariable UUID tenant,@AuthenticationPrincipal Jwt jwt) { return service.summary(tenant,jwt.getSubject()); }
    @GetMapping("/incidents/{id}")
    Incident get(@PathVariable UUID tenant,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.get(tenant,jwt.getSubject(),id); }
    @PostMapping("/incidents")
    @ResponseStatus(CREATED)
    Incident create(@PathVariable UUID tenant,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody IncidentInput input) {
        return service.create(tenant,jwt.getSubject(),input);
    }
    @PatchMapping("/incidents/{id}")
    Incident update(@PathVariable UUID tenant,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody IncidentUpdate input) {
        return service.update(tenant,jwt.getSubject(),id,input);
    }
    @GetMapping("/incidents/{id}/activity")
    List<Activity> activity(@PathVariable UUID tenant,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.activity(tenant,jwt.getSubject(),id); }
    @PostMapping("/incidents/{id}/comments")
    @ResponseStatus(CREATED)
    void comment(@PathVariable UUID tenant,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CommentInput input) { service.comment(tenant,jwt.getSubject(),id,input); }
}
