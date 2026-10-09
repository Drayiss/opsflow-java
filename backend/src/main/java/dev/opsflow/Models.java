package dev.opsflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class Models {
    private Models() {}
    public enum Role { ADMIN, RESPONDER, VIEWER }
    public enum Severity { SEV1, SEV2, SEV3, SEV4 }
    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED }
    public record Organization(UUID id, String name, Role role) {}
    public record OrganizationInput(@NotBlank @Size(max=120) String name) {}
    public record Member(String subject, String displayName, Role role) {}
    public record MemberInput(@NotBlank @Size(max=200) String subject,
                              @NotBlank @Size(max=120) String displayName, @NotNull Role role) {}
    public record Incident(UUID id, UUID organizationId, String title, String description, Severity severity,
                           Status status, String assignee, String createdBy, int version, Instant createdAt, Instant updatedAt) {}
    public record IncidentInput(@NotBlank @Size(max=160) String title, @NotNull @Size(max=8000) String description,
                                @NotNull Severity severity) {}
    public record IncidentUpdate(@NotNull Status status, @Size(max=200) String assignee, @Min(0) int version) {}
    public record CommentInput(@NotBlank @Size(max=2000) String message) {}
    public record Activity(UUID id, String actor, String message, Instant createdAt) {}
    public record Summary(long total, long open, long acknowledged, long resolved, long critical) {}
    public record Page<T>(List<T> items, int page, int size, boolean hasMore) {}
    public record Notification(UUID id, UUID incidentId, String message, Instant readAt, Instant createdAt) {}
    public record IncidentEvent(UUID eventId, UUID organizationId, UUID incidentId, String message) {
        public void validate() {
            if (eventId == null || organizationId == null || incidentId == null || message == null ||
                message.isBlank() || message.length() > 500) throw new IllegalArgumentException("Invalid incident event");
        }
    }
}
