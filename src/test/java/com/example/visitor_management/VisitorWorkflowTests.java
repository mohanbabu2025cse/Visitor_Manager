package com.example.visitor_management;
import com.example.visitor_management.dto.*;
import com.example.visitor_management.dto.Requests.*;
import com.example.visitor_management.exception.ApiException;
import com.example.visitor_management.model.*;
import com.example.visitor_management.repository.*;
import com.example.visitor_management.service.VisitorService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:visitor-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.sms.enabled=false"
})
class VisitorWorkflowTests {
    @Autowired VisitorService service;
    @Autowired OTPPassRepository passes;
    @Autowired ApprovalRepository approvals;
    @Autowired ResidentRepository residents;
    @Autowired FlatRepository flats;
    @Value("${local.server.port}") int port;
    Long residentId;
    @BeforeEach void setup() {
        passes.deleteAll(); approvals.deleteAll(); residents.deleteAll(); flats.deleteAll();
        Flat flat = service.addFlat(new FlatRequest("A-101"));
        residentId = service.addResident(new ResidentRequest("Anu", "9876543210", flat.getId())).getId();
    }
    ApprovalView visit(LocalDateTime start, LocalDateTime end) {
        return service.approve(residentId, new ApprovalRequest("Ravi", "9876543211", "Family visit", start, end));
    }
    ApprovalView active() { return visit(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1)); }
    EntryRequest entry(ApprovalView v) { return new EntryRequest(v.id(), v.otp()); }
    @Test void entryIsRecordedAndPassCannotBeReused() {
        var v = active(); assertTrue(v.otp().matches("[0-9]{6}"));
        var result = service.enter(entry(v));
        assertEquals(VisitStatus.ENTERED, result.status()); assertNotNull(result.entryTime());
        assertNull(result.otp()); assertTrue(passes.findByApprovalId(v.id()).orElseThrow().isUsed());
        assertThrows(ApiException.class, () -> service.enter(entry(v)));
    }
    @Test void incorrectOtpDoesNotConsumePass() {
        var v = active();
        String wrong = v.otp().equals("000000") ? "111111" : "000000";
        assertThrows(ApiException.class, () -> service.enter(new EntryRequest(v.id(), wrong)));
        assertEquals(VisitStatus.ENTERED, service.enter(entry(v)).status());
    }
    @Test void revokedPassCannotEnter() {
        var v = active(); assertEquals(VisitStatus.REVOKED, service.revoke(residentId, v.id()).status());
        assertThrows(ApiException.class, () -> service.enter(entry(v)));
    }
    @Test void expiredPassIsBlockedBeforeScheduledCleanup() {
        var v = active(); var a = approvals.findById(v.id()).orElseThrow();
        a.setExpiresAt(LocalDateTime.now().minusSeconds(1)); approvals.save(a);
        assertEquals(VisitStatus.EXPIRED, service.list(residentId).getFirst().status());
        assertThrows(ApiException.class, () -> service.enter(entry(v)));
        assertThrows(ApiException.class, () -> service.revoke(residentId, v.id()));
        service.expireUnused();
        assertEquals(VisitStatus.EXPIRED, approvals.findById(v.id()).orElseThrow().getStatus());
    }
    @Test void futureVisitCannotEnterEarly() {
        var v = visit(LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
        assertThrows(ApiException.class, () -> service.enter(entry(v)));
    }
    @Test void invalidVisitWindowsAreRejected() {
        var now = LocalDateTime.now();
        assertThrows(ApiException.class, () -> visit(now.plusHours(2), now.plusHours(1)));
        assertThrows(ApiException.class, () -> visit(now.minusHours(2), now.minusHours(1)));
    }
    @Test void anotherResidentCannotRevoke() {
        var v = active();
        Long other = service.addResident(new ResidentRequest("Bala", "9876543212", flats.findAll().getFirst().getId())).getId();
        assertThrows(ApiException.class, () -> service.revoke(other, v.id()));
    }
    @Test void enteredVisitsCannotBeRevoked() {
        var v = active(); service.enter(entry(v));
        assertThrows(ApiException.class, () -> service.revoke(residentId, v.id()));
        service.expireUnused(); assertEquals(VisitStatus.ENTERED, service.list(residentId).getFirst().status());
    }
    @Test void duplicateFlatsAndMissingResidentsAreRejected() {
        assertThrows(ApiException.class, () -> service.addFlat(new FlatRequest(" a-101 ")));
        assertThrows(ApiException.class, () -> service.list(999999L));
    }
    @Test void editingFlatPersistsAddressWithoutCreatingAnotherFlat() throws Exception {
        Long id = flats.findAll().getFirst().getId();
        var response = request("/api/flats/" + id, "PUT",
            "{\"number\":\"A-102\",\"address\":\"12 College Road, Coimbatore\"}");
        assertEquals(200, response.statusCode());
        assertEquals(1, flats.count());
        assertEquals("12 College Road, Coimbatore", flats.findById(id).orElseThrow().getAddress());
        assertTrue(request("/api/residents", "GET", "").body().contains("A-102"));
        assertEquals(400, request("/api/flats/" + id, "PUT", "{\"number\":\"\"}").statusCode());
        assertEquals("A-102", flats.findById(id).orElseThrow().getNumber());
    }
    @Test void editingResidentPersistsDetailsAndNewHomeFlat() throws Exception {
        var flat = service.addFlat(new FlatRequest("B-202", "North Block"));
        var response = request("/api/residents/" + residentId, "PUT",
            "{\"name\":\"Anu Updated\",\"phone\":\"9876543299\",\"flatId\":" + flat.getId() + "}");
        assertEquals(200, response.statusCode());
        assertEquals(1, residents.count());
        var saved = residents.findById(residentId).orElseThrow();
        assertEquals("Anu Updated", saved.getName());
        assertEquals("9876543299", saved.getPhone());
        assertEquals(flat.getId(), saved.getFlat().getId());
    }
    @Test void editsRejectDuplicatesAndMissingRecords() {
        var first = flats.findAll().getFirst();
        service.addFlat(new FlatRequest("B-202"));
        assertThrows(ApiException.class, () -> service.updateFlat(first.getId(), new FlatRequest("B-202")));
        assertThrows(ApiException.class, () -> service.updateFlat(999999L, new FlatRequest("C-303")));
        assertThrows(ApiException.class, () -> service.updateResident(residentId,
            new ResidentRequest("Changed", "9876543299", 999999L)));
        assertEquals("Anu", residents.findById(residentId).orElseThrow().getName());
    }
    @Test void simultaneousRequestsAllowOnlyOneEntry() throws Exception {
        var v = active();
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> {
                start.await();
                try { service.enter(entry(v)); return true; } catch (ApiException e) { return false; }
            };
            var first = executor.submit(attempt); var second = executor.submit(attempt); start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }
    HttpResponse<String> request(String path, String method, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Accept", path.equals("/") ? "text/html" : "application/json")
            .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void invalidHttpRequestsReturnClearErrors() throws Exception {
        assertEquals(400, request("/api/flats", "POST", "{\"number\":\"\"}").statusCode());
        assertEquals(400, request("/api/security/entries", "POST", "{\"approvalId\":1,\"otp\":\"abc\"}").statusCode());
        assertEquals(400, request("/api/flats", "POST", "{").statusCode());
        assertEquals(404, request("/api/residents/999999/approvals", "GET", "").statusCode());
        assertEquals(400, request("/api/residents/0/approvals", "GET", "").statusCode());
    }
    @Test void browserAndApiAreServed() throws Exception {
        assertTrue(request("/api/sms/status", "GET", "").body().contains("false"));
        assertEquals(503, request("/api/residents/1/approvals/1/sms", "POST", "").statusCode());
        assertEquals(200, request("/", "GET", "").statusCode());
        assertEquals(201, request("/api/flats", "POST", "{\"number\":\"B-202\"}").statusCode());
        assertTrue(request("/api/residents", "GET", "").body().contains("Anu"));
    }
}
