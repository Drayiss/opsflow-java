package dev.opsflow;

import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.opsflow.Models.*;

@Service
class NotificationConsumer {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    NotificationConsumer(JdbcTemplate db,ObjectMapper json) { this.db=db;this.json=json; }
    @Transactional
    public void consume(String payload) {
        IncidentEvent event;
        try { event=json.readValue(payload,IncidentEvent.class); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Malformed event",ex); }
        if(event==null) throw new IllegalArgumentException("Empty event");
        event.validate();
        Long count=db.queryForObject("SELECT count(*) FROM incidents WHERE organization_id=? AND id=?",Long.class,event.organizationId(),event.incidentId());
        if (count==null || count!=1) throw new IllegalArgumentException("Event references an unknown tenant incident");
        // Insertion and all notifications commit together. Redelivery after a lost ACK is harmless.
        if (db.update("INSERT INTO processed_events(event_id) VALUES (?) ON CONFLICT DO NOTHING",event.eventId())==0) return;
        var subjects=db.queryForList("SELECT subject FROM memberships WHERE organization_id=?",String.class,event.organizationId());
        for (String subject:subjects) db.update("INSERT INTO notifications(id,event_id,organization_id,subject,incident_id,message) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID(),event.eventId(),event.organizationId(),subject,event.incidentId(),event.message());
    }
}
