package dev.opsflow;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import static dev.opsflow.Models.*;

@RestController
@RequestMapping("/api/organizations/{tenant}")
class NotificationController {
    private final JdbcTemplate db;
    private final TenantAccess access;
    NotificationController(JdbcTemplate db,TenantAccess access) { this.db=db;this.access=access; }
    @GetMapping("/notifications")
    List<Notification> list(@PathVariable UUID tenant,@AuthenticationPrincipal Jwt jwt) {
        access.require(tenant,jwt.getSubject());
        return db.query("SELECT * FROM notifications WHERE organization_id=? AND subject=? ORDER BY created_at DESC,id DESC LIMIT 50",
            (rs,n)->new Notification(rs.getObject("id",UUID.class),rs.getObject("incident_id",UUID.class),rs.getString("message"),
                rs.getTimestamp("read_at")==null?null:rs.getTimestamp("read_at").toInstant(),rs.getTimestamp("created_at").toInstant()),tenant,jwt.getSubject());
    }
    @PatchMapping("/notifications/{id}/read")
    void read(@PathVariable UUID tenant,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        access.require(tenant,jwt.getSubject());
        if(db.update("UPDATE notifications SET read_at=COALESCE(read_at,now()) WHERE organization_id=? AND subject=? AND id=?",tenant,jwt.getSubject(),id)!=1)
            throw new ResponseStatusException(NOT_FOUND,"Notification not found");
    }
    @GetMapping("/deliveries")
    List<Map<String,Object>> deliveries(@PathVariable UUID tenant,@AuthenticationPrincipal Jwt jwt) {
        access.require(tenant,jwt.getSubject(),Role.ADMIN);
        return db.queryForList("SELECT id,attempts,available_at,published_at,dead_at,last_error,created_at FROM outbox WHERE organization_id=? ORDER BY created_at DESC LIMIT 100",tenant);
    }
    @PostMapping("/deliveries/{id}/retry")
    void retry(@PathVariable UUID tenant,@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
        access.require(tenant,jwt.getSubject(),Role.ADMIN);
        if(db.update("UPDATE outbox SET attempts=0,dead_at=NULL,last_error=NULL,available_at=now() WHERE organization_id=? AND id=? AND dead_at IS NOT NULL AND published_at IS NULL",tenant,id)!=1)
            throw new ResponseStatusException(NOT_FOUND,"Failed delivery not found");
    }
}
