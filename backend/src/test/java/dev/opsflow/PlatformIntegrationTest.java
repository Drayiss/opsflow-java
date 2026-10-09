package dev.opsflow;

import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static dev.opsflow.Models.*;

@SpringBootTest(properties={"opsflow.cache-enabled=true","opsflow.notifications.dispatch-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTest {
    static final RSAKey signingKey=key();
    static final HttpServer keys=keyServer();
    static RSAKey key() { try{return new RSAKeyGenerator(2048).keyID("test-key").generate();}catch(Exception e){throw new IllegalStateException(e);} }
    static HttpServer keyServer() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/jwks",exchange->{
                byte[] body=new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
                try(var stream=exchange.getResponseBody()){stream.write(body);}
            });server.start();return server;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
        registry.add("spring.data.redis.host",redis::getHost);
        registry.add("spring.data.redis.port",()->redis.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",()->"https://issuer.example.test");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->"http://127.0.0.1:"+keys.getAddress().getPort()+"/jwks");
    }
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired NotificationConsumer consumer;
    @Autowired TransactionTemplate transaction;
    @Autowired StringRedisTemplate cache;
    UUID acme,other;
    RequestPostProcessor as(String subject) { return jwt().jwt(j->j.subject(subject).claim("name",subject)); }
    @BeforeEach void seed() throws Exception {
        db.execute("TRUNCATE organizations, memberships, incidents, incident_activity, outbox, processed_events, notifications CASCADE");
        acme=org("alice","Acme"); other=org("eve","Other");
        db.update("INSERT INTO memberships VALUES (?, 'bob', 'Bob', 'RESPONDER'), (?, 'viewer', 'Viewer', 'VIEWER')",acme,acme);
    }
    UUID org(String user,String name) throws Exception {
        var response=http.perform(post("/api/organizations").with(as(user)).contentType("application/json").content(json.writeValueAsString(new OrganizationInput(name))))
            .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("id").asText());
    }
    Incident incident() throws Exception {
        return json.readValue(http.perform(post("/api/organizations/"+acme+"/incidents").with(as("bob")).contentType("application/json")
            .content("{\"title\":\"Database unavailable\",\"description\":\"Impact\",\"severity\":\"SEV1\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),Incident.class);
    }
    @Test void unauthenticatedAndInvalidBearerAreRejected() throws Exception {
        http.perform(get("/api/organizations")).andExpect(status().isUnauthorized());
        http.perform(get("/api/organizations").header("Authorization","Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }
    String signed(String issuer,String audience,Instant expires,RSAKey key) throws Exception {
        var token=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
            new JWTClaimsSet.Builder().subject("alice").issuer(issuer).audience(audience).issueTime(Date.from(Instant.now().minusSeconds(600)))
                .expirationTime(Date.from(expires)).build());
        token.sign(new RSASSASigner(key));return token.serialize();
    }
    @Test void realJwtSignatureIssuerAudienceAndExpiryAreValidated() throws Exception {
        String trusted="https://issuer.example.test";
        http.perform(get("/api/organizations").header("Authorization","Bearer "+signed(trusted,"opsflow-api",Instant.now().plusSeconds(300),signingKey)))
            .andExpect(status().isOk());
        for(String token:List.of(signed("https://untrusted.example.test","opsflow-api",Instant.now().plusSeconds(300),signingKey),
            signed(trusted,"another-api",Instant.now().plusSeconds(300),signingKey),
            signed(trusted,"opsflow-api",Instant.now().minusSeconds(120),signingKey),
            signed(trusted,"opsflow-api",Instant.now().plusSeconds(300),key())))
            http.perform(get("/api/organizations").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @AfterAll static void stopKeys(){keys.stop(0);}
    @Test void tenantsCannotReadOrModifyEachOthersData() throws Exception {
        Incident incident=incident();
        String root="/api/organizations/"+acme;
        for(String path:List.of("/incidents","/incidents/"+incident.id(),"/incidents/"+incident.id()+"/activity","/summary","/members","/notifications","/deliveries"))
            http.perform(get(root+path).with(as("eve"))).andExpect(status().isNotFound());
        http.perform(patch(root+"/incidents/"+incident.id()).with(as("eve")).contentType("application/json").content("{\"status\":\"ACKNOWLEDGED\",\"version\":0}"))
            .andExpect(status().isNotFound());
        http.perform(get("/api/organizations/"+other+"/incidents/"+incident.id()).with(as("eve"))).andExpect(status().isNotFound());
        http.perform(get("/api/organizations").with(as("eve"))).andExpect(jsonPath("$.length()").value(1));
        http.perform(post(root+"/incidents/"+incident.id()+"/comments").with(as("eve")).contentType("application/json").content("{\"message\":\"intrusion\"}"))
            .andExpect(status().isNotFound());
    }
    @Test void rolesWorkflowAndOptimisticLockingAreEnforced() throws Exception {
        Incident incident=incident();String path="/api/organizations/"+acme+"/incidents/"+incident.id();
        http.perform(get(path).with(as("viewer"))).andExpect(status().isOk());
        http.perform(patch(path).with(as("viewer")).contentType("application/json").content("{\"status\":\"ACKNOWLEDGED\",\"version\":0}"))
            .andExpect(status().isForbidden());
        http.perform(patch(path).with(as("bob")).contentType("application/json").content("{\"status\":\"RESOLVED\",\"version\":0}"))
            .andExpect(status().isConflict());
        http.perform(patch(path).with(as("bob")).contentType("application/json").content("{\"status\":\"ACKNOWLEDGED\",\"assignee\":\"eve\",\"version\":0}"))
            .andExpect(status().isBadRequest());
        http.perform(patch(path).with(as("bob")).contentType("application/json").content("{\"status\":\"ACKNOWLEDGED\",\"assignee\":\"bob\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        http.perform(patch(path).with(as("alice")).contentType("application/json").content("{\"status\":\"ACKNOWLEDGED\",\"version\":0}"))
            .andExpect(status().isConflict());
        http.perform(patch(path).with(as("bob")).contentType("application/json").content("{\"status\":\"RESOLVED\",\"version\":1}"))
            .andExpect(status().isOk());
        http.perform(patch(path).with(as("bob")).contentType("application/json").content("{\"status\":\"OPEN\",\"version\":2}"))
            .andExpect(status().isConflict());
        http.perform(patch(path).with(as("alice")).contentType("application/json").content("{\"status\":\"OPEN\",\"version\":2}"))
            .andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM outbox",Long.class)).isEqualTo(4L);
        assertThat(db.queryForObject("SELECT count(*) FROM incident_activity",Long.class)).isEqualTo(4L);
    }
    @Test void administratorsCannotRemoveLastAdministratorOrEscalateAcrossTenants() throws Exception {
        String path="/api/organizations/"+acme+"/members";
        http.perform(put(path).with(as("bob")).contentType("application/json").content("{\"subject\":\"bob\",\"displayName\":\"Bob\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());
        http.perform(put(path).with(as("alice")).contentType("application/json").content("{\"subject\":\"alice\",\"displayName\":\"Alice\",\"role\":\"VIEWER\"}"))
            .andExpect(status().isConflict());
        db.update("UPDATE memberships SET role='VIEWER' WHERE subject='bob'");
        http.perform(post("/api/organizations/"+acme+"/incidents").with(as("bob")).contentType("application/json").content("{\"title\":\"x\",\"description\":\"\",\"severity\":\"SEV3\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void concurrentDuplicateEventsCreateExactlyOneNotificationPerMember() throws Exception {
        incident();String payload=db.queryForObject("SELECT payload::text FROM outbox",String.class);
        try(var executor=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Future<?>>();for(int i=0;i<8;i++)tasks.add(executor.submit(()->consumer.consume(payload)));
            for(var task:tasks)task.get(30,TimeUnit.SECONDS);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM processed_events",Long.class)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications",Long.class)).isEqualTo(3L);
        http.perform(get("/api/organizations/"+other+"/notifications").with(as("eve"))).andExpect(jsonPath("$.length()").value(0));
        UUID notice=db.queryForObject("SELECT id FROM notifications WHERE subject='alice'",UUID.class);
        http.perform(patch("/api/organizations/"+acme+"/notifications/"+notice+"/read").with(as("bob"))).andExpect(status().isNotFound());
    }
    @Test void failedConsumerTransactionCanBeRetriedWithoutLosingNotifications() throws Exception {
        incident();String payload=db.queryForObject("SELECT payload::text FROM outbox",String.class);
        db.execute("ALTER TABLE notifications ADD CONSTRAINT injected_failure CHECK (subject <> 'bob')");
        try {
            assertThatThrownBy(()->consumer.consume(payload)).isInstanceOf(RuntimeException.class);
            assertThat(db.queryForObject("SELECT count(*) FROM processed_events",Long.class)).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM notifications",Long.class)).isZero();
        }finally{db.execute("ALTER TABLE notifications DROP CONSTRAINT injected_failure");}
        consumer.consume(payload);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications",Long.class)).isEqualTo(3L);
    }
    @Test void invalidEventsAndInvalidRequestsDoNotCommitData() throws Exception {
        Incident incident=incident();
        assertThatThrownBy(()->consumer.consume("not-json")).isInstanceOf(IllegalArgumentException.class);
        String crossed=json.writeValueAsString(new IncidentEvent(UUID.randomUUID(),other,incident.id(),"bad"));
        assertThatThrownBy(()->consumer.consume(crossed)).isInstanceOf(IllegalArgumentException.class);
        http.perform(post("/api/organizations/"+acme+"/incidents").with(as("alice")).contentType("application/json").content("{\"title\":\" \",\"description\":\"\",\"severity\":\"SEV1\"}"))
            .andExpect(status().isBadRequest());
        http.perform(get("/api/organizations/"+acme+"/incidents?size=1000").with(as("alice"))).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT count(*) FROM outbox",Long.class)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT count(*) FROM processed_events",Long.class)).isZero();
    }
    @Test void summariesUseTenantCacheAndInvalidateAfterCommittedWrites() throws Exception {
        incident();String summary="/api/organizations/"+acme+"/summary";
        http.perform(get(summary).with(as("alice"))).andExpect(jsonPath("$.total").value(1));
        assertThat(cache.hasKey("opsflow:summary:"+acme)).isTrue();
        db.update("INSERT INTO incidents(id,organization_id,title,description,severity,status,created_by) VALUES (?,?,'Bypass cache','','SEV3','OPEN','alice')",UUID.randomUUID(),acme);
        http.perform(get(summary).with(as("alice"))).andExpect(jsonPath("$.total").value(1));
        http.perform(get("/api/organizations/"+other+"/summary").with(as("eve"))).andExpect(jsonPath("$.total").value(0));
        incident();
        http.perform(get(summary).with(as("alice"))).andExpect(jsonPath("$.total").value(3));
        http.perform(get(summary).with(as("eve"))).andExpect(status().isNotFound());
    }
    @Test void outboxProcessesEventsAndPersistsRetryDeadState() throws Exception {
        incident();
        var dispatcher=new OutboxDispatcher(db,transaction,consumer,new StaticListableBeanFactory().getBeanProvider(AzureBus.class));
        dispatcher.dispatch();
        assertThat(db.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NOT NULL",Long.class)).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT count(*) FROM notifications",Long.class)).isEqualTo(3L);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO outbox(id,organization_id,payload,attempts) VALUES (?,?,'{}',0)",id,acme);
        dispatcher.dispatch();
        assertThat(db.queryForObject("SELECT attempts FROM outbox WHERE id=?",Integer.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT available_at>now() FROM outbox WHERE id=?",Boolean.class,id)).isTrue();
        db.update("UPDATE outbox SET attempts=7,available_at=now() WHERE id=?",id);
        dispatcher.dispatch();
        assertThat(db.queryForObject("SELECT dead_at IS NOT NULL FROM outbox WHERE id=?",Boolean.class,id)).isTrue();
        http.perform(post("/api/organizations/"+acme+"/deliveries/"+id+"/retry").with(as("bob"))).andExpect(status().isForbidden());
        http.perform(post("/api/organizations/"+other+"/deliveries/"+id+"/retry").with(as("eve"))).andExpect(status().isNotFound());
        http.perform(post("/api/organizations/"+acme+"/deliveries/"+id+"/retry").with(as("alice"))).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT attempts FROM outbox WHERE id=?",Integer.class,id)).isZero();
    }
}
