package dev.opsflow;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import static dev.opsflow.Models.*;

@Component
class TenantAccess {
    private final JdbcTemplate db;
    TenantAccess(JdbcTemplate db) { this.db = db; }
    Role require(UUID tenant, String subject, Role... allowed) {
        var roles = db.query("SELECT role FROM memberships WHERE organization_id=? AND subject=?",
            (rs,n) -> Role.valueOf(rs.getString(1)), tenant, subject);
        if (roles.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Organization not found");
        var role = roles.getFirst();
        if (allowed.length == 0) return role;
        for (var candidate : allowed) if (role == candidate) return role;
        throw new ResponseStatusException(FORBIDDEN, "Your organization role does not allow this action");
    }
}
