package dev.opsflow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
import static dev.opsflow.Models.*;

@Service
class IncidentService {
    private final JdbcTemplate db;
    private final TenantAccess access;
    private final SummaryCache cache;
    private final ObjectMapper json;
    IncidentService(JdbcTemplate db,TenantAccess access,SummaryCache cache,ObjectMapper json) {
        this.db=db;this.access=access;this.cache=cache;this.json=json;
    }
    static Incident map(ResultSet rs,int row) throws SQLException {
        return new Incident(rs.getObject("id",UUID.class),rs.getObject("organization_id",UUID.class),
            rs.getString("title"),rs.getString("description"),Severity.valueOf(rs.getString("severity")),
            Status.valueOf(rs.getString("status")),rs.getString("assignee"),rs.getString("created_by"),
            rs.getInt("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }
    Page<Incident> list(UUID tenant,String subject,Status status,int page,int size) {
        access.require(tenant,subject);
        if (page<0 || page>10000 || size<1 || size>100) throw new ResponseStatusException(BAD_REQUEST,"Page must be 0–10000 and size 1–100");
        String sql="SELECT * FROM incidents WHERE organization_id=?"+(status==null?"":" AND status=?")+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?";
        Object[] args=status==null?new Object[]{tenant,size+1,page*size}:new Object[]{tenant,status.name(),size+1,page*size};
        var rows=db.query(sql,IncidentService::map,args);
        return new Page<>(rows.stream().limit(size).toList(),page,size,rows.size()>size);
    }
    Incident get(UUID tenant,String subject,UUID id) { access.require(tenant,subject); return find(tenant,id); }
    private Incident find(UUID tenant,UUID id) {
        return db.query("SELECT * FROM incidents WHERE organization_id=? AND id=?",IncidentService::map,tenant,id)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Incident not found"));
    }
    Summary summary(UUID tenant,String subject) {
        access.require(tenant,subject);
        return cache.get(tenant,()->db.queryForObject("SELECT count(*) total,count(*) FILTER(WHERE status='OPEN') open,count(*) FILTER(WHERE status='ACKNOWLEDGED') acknowledged,count(*) FILTER(WHERE status='RESOLVED') resolved,count(*) FILTER(WHERE severity='SEV1' AND status<>'RESOLVED') critical FROM incidents WHERE organization_id=?",
            (rs,n)->new Summary(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5)),tenant));
    }
    @Transactional
    Incident create(UUID tenant,String subject,IncidentInput input) {
        access.require(tenant,subject,Role.ADMIN,Role.RESPONDER);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO incidents(id,organization_id,title,description,severity,status,created_by) VALUES (?,?,?,?,?,'OPEN',?)",id,tenant,input.title().trim(),input.description(),input.severity().name(),subject);
        record(tenant,id,subject,"Created "+input.severity().name()+" incident: "+input.title().trim());
        return find(tenant,id);
    }
    @Transactional
    Incident update(UUID tenant,String subject,UUID id,IncidentUpdate input) {
        Role role=access.require(tenant,subject,Role.ADMIN,Role.RESPONDER);
        Incident current=find(tenant,id);
        if (current.version()!=input.version()) throw new ResponseStatusException(CONFLICT,"Incident changed. Refresh and try again.");
        Status next=input.status();
        boolean valid=next==current.status() || (current.status()==Status.OPEN && next==Status.ACKNOWLEDGED)
            || (current.status()==Status.ACKNOWLEDGED && next==Status.RESOLVED)
            || (role==Role.ADMIN && current.status()==Status.RESOLVED && next==Status.OPEN);
        if (!valid) throw new ResponseStatusException(CONFLICT,"Allowed workflow: open → acknowledged → resolved. Administrators can reopen.");
        if (input.assignee()!=null) {
            var members=db.queryForList("SELECT role FROM memberships WHERE organization_id=? AND subject=?",String.class,tenant,input.assignee());
            if (members.isEmpty() || members.getFirst().equals("VIEWER")) throw new ResponseStatusException(BAD_REQUEST,"Assign a responder or administrator in this organization");
        }
        int changed=db.update("UPDATE incidents SET status=?,assignee=?,version=version+1,updated_at=now() WHERE organization_id=? AND id=? AND version=?",
            next.name(),input.assignee(),tenant,id,input.version());
        if (changed!=1) throw new ResponseStatusException(CONFLICT,"Incident changed. Refresh and try again.");
        record(tenant,id,subject,"Status: "+next.name()+" · Assignee: "+(input.assignee()==null?"unassigned":input.assignee()));
        return find(tenant,id);
    }
    List<Activity> activity(UUID tenant,String subject,UUID id) {
        get(tenant,subject,id);
        return db.query("SELECT * FROM incident_activity WHERE organization_id=? AND incident_id=? ORDER BY created_at DESC,id DESC LIMIT 200",
            (rs,n)->new Activity(rs.getObject("id",UUID.class),rs.getString("actor"),rs.getString("message"),rs.getTimestamp("created_at").toInstant()),tenant,id);
    }
    @Transactional
    void comment(UUID tenant,String subject,UUID id,CommentInput input) {
        access.require(tenant,subject,Role.ADMIN,Role.RESPONDER); find(tenant,id);
        record(tenant,id,subject,input.message().trim());
    }
    private void record(UUID tenant,UUID id,String actor,String message) {
        db.update("INSERT INTO incident_activity(id,organization_id,incident_id,actor,message) VALUES (?,?,?,?,?)",UUID.randomUUID(),tenant,id,actor,message);
        UUID eventId=UUID.randomUUID();
        try {
            String payload=json.writeValueAsString(new IncidentEvent(eventId,tenant,id,message.substring(0,Math.min(500,message.length()))));
            db.update("INSERT INTO outbox(id,organization_id,payload) VALUES (?,?,?::jsonb)",eventId,tenant,payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.evict(tenant); }
        });
    }
}
