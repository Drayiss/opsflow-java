package dev.opsflow;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import static dev.opsflow.Models.*;

@RestController
@RequestMapping("/api/organizations")
class OrganizationController {
    private final JdbcTemplate db;
    private final TenantAccess access;
    OrganizationController(JdbcTemplate db, TenantAccess access) { this.db=db; this.access=access; }
    @GetMapping
    List<Organization> list(@AuthenticationPrincipal Jwt jwt) {
        return db.query("SELECT o.id,o.name,m.role FROM organizations o JOIN memberships m ON m.organization_id=o.id WHERE m.subject=? ORDER BY o.name,o.id",
            (rs,n) -> new Organization(rs.getObject("id",UUID.class),rs.getString("name"),Role.valueOf(rs.getString("role"))),jwt.getSubject());
    }
    @PostMapping
    @ResponseStatus(CREATED)
    @Transactional
    Organization create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrganizationInput input) {
        var id=UUID.randomUUID();
        db.update("INSERT INTO organizations(id,name) VALUES (?,?)",id,input.name().trim());
        String name=Optional.ofNullable(jwt.getClaimAsString("name")).orElse(jwt.getSubject());
        db.update("INSERT INTO memberships(organization_id,subject,display_name,role) VALUES (?,?,?,'ADMIN')",id,jwt.getSubject(),name.substring(0,Math.min(120,name.length())));
        return new Organization(id,input.name().trim(),Role.ADMIN);
    }
    @PutMapping("/{tenant}")
    Organization rename(@PathVariable UUID tenant, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OrganizationInput input) {
        access.require(tenant,jwt.getSubject(),Role.ADMIN);
        db.update("UPDATE organizations SET name=? WHERE id=?",input.name().trim(),tenant);
        return new Organization(tenant,input.name().trim(),Role.ADMIN);
    }
    @GetMapping("/{tenant}/members")
    List<Member> members(@PathVariable UUID tenant, @AuthenticationPrincipal Jwt jwt) {
        access.require(tenant,jwt.getSubject());
        return db.query("SELECT subject,display_name,role FROM memberships WHERE organization_id=? ORDER BY display_name,subject",
            (rs,n)->new Member(rs.getString(1),rs.getString(2),Role.valueOf(rs.getString(3))),tenant);
    }
    @PutMapping("/{tenant}/members")
    @Transactional
    void member(@PathVariable UUID tenant, @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MemberInput input) {
        // Serialize member changes and protect the final administrator.
        db.queryForList("SELECT id FROM organizations WHERE id=? FOR UPDATE",tenant);
        access.require(tenant,jwt.getSubject(),Role.ADMIN);
        if (input.role()!=Role.ADMIN) {
            var current=db.queryForList("SELECT role FROM memberships WHERE organization_id=? AND subject=?",String.class,tenant,input.subject());
            Long admins=db.queryForObject("SELECT count(*) FROM memberships WHERE organization_id=? AND role='ADMIN'",Long.class,tenant);
            if (current.contains("ADMIN") && admins!=null && admins<=1) throw new ResponseStatusException(CONFLICT,"Keep at least one administrator");
        }
        db.update("INSERT INTO memberships(organization_id,subject,display_name,role) VALUES (?,?,?,?) ON CONFLICT(organization_id,subject) DO UPDATE SET display_name=excluded.display_name,role=excluded.role",
            tenant,input.subject(),input.displayName().trim(),input.role().name());
    }
}
