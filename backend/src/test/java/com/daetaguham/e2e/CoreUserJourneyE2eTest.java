package com.daetaguham.e2e;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.datasource.url=jdbc:h2:mem:core-user-journey;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@Transactional
class CoreUserJourneyE2eTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void coverRequestFlowsFromSignupToApprovedScheduleAndReadNotifications() throws Exception {
		Session owner = signUpAndLogin("010-7000-1000", "김효주");
		Session requester = signUpAndLogin("010-7000-2000", "김민");
		Session applicant = signUpAndLogin("010-7000-3000", "이진호");

		MvcResult createdStore = mockMvc.perform(post("/stores")
				.header(HttpHeaders.AUTHORIZATION, owner.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"성수점",
						  "category":"아이스크림·디저트",
						  "address":"서울 성동구"
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerName").value("김효주"))
				.andReturn();
		Long storeId = jsonLong(createdStore, "$.id");
		String inviteCode = JsonPath.read(createdStore.getResponse().getContentAsString(), "$.inviteCode");

		joinAndActivate(storeId, inviteCode, requester, owner);
		joinAndActivate(storeId, inviteCode, applicant, owner);

		LocalDate shiftDate = LocalDate.now().plusDays(14);
		MvcResult createdShift = mockMvc.perform(post("/stores/{storeId}/shifts", storeId)
				.header(HttpHeaders.AUTHORIZATION, owner.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workerId":%d,
						  "startAt":"%sT16:00:00",
						  "endAt":"%sT22:00:00",
						  "position":"마감"
						}
						""".formatted(requester.userId(), shiftDate, shiftDate)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.workerName").value("김민"))
				.andReturn();
		Long shiftId = jsonLong(createdShift, "$.id");

		mockMvc.perform(get("/me/shifts")
				.param("from", shiftDate.toString())
				.param("to", shiftDate.toString())
				.header(HttpHeaders.AUTHORIZATION, requester.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(shiftId))
				.andExpect(jsonPath("$[0].workerName").value("김민"));

		MvcResult createdRequest = mockMvc.perform(post("/requests")
				.header(HttpHeaders.AUTHORIZATION, requester.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "type":"COVER",
						  "shiftId":%d,
						  "mode":"PUBLIC",
						  "scope":"STORE",
						  "reason":"개인 사정"
						}
						""".formatted(shiftId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andReturn();
		Long requestId = jsonLong(createdRequest, "$.id");

		mockMvc.perform(get("/requests")
				.header(HttpHeaders.AUTHORIZATION, applicant.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].id").value(requestId))
				.andExpect(jsonPath("$.content[0].requesterName").value("김민"));

		MvcResult applied = mockMvc.perform(post("/requests/{requestId}/applications", requestId)
				.header(HttpHeaders.AUTHORIZATION, applicant.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"message\":\"제가 대신 근무할게요\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.applicantName").value("이진호"))
				.andExpect(jsonPath("$.status").value("APPLIED"))
				.andReturn();
		Long applicationId = jsonLong(applied, "$.id");

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, requester.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("APPLICATION_NEW"))
				.andExpect(jsonPath("$.content[0].requestId").value(requestId));

		mockMvc.perform(put("/requests/{requestId}/selection", requestId)
				.header(HttpHeaders.AUTHORIZATION, requester.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"applicationId\":" + applicationId + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("APPROVAL_NEEDED"))
				.andExpect(jsonPath("$.content[0].requestId").value(requestId));

		mockMvc.perform(get("/stores/{storeId}/approvals", storeId)
				.header(HttpHeaders.AUTHORIZATION, owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].request.id").value(requestId))
				.andExpect(jsonPath("$[0].changes[0].before.workerName").value("김민"))
				.andExpect(jsonPath("$[0].changes[0].after.workerName").value("이진호"));

		mockMvc.perform(put("/requests/{requestId}/approval", requestId)
				.header(HttpHeaders.AUTHORIZATION, owner.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.shift.workerName").value("이진호"));

		mockMvc.perform(get("/me/shifts")
				.param("from", shiftDate.toString())
				.param("to", shiftDate.toString())
				.header(HttpHeaders.AUTHORIZATION, requester.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/me/shifts")
				.param("from", shiftDate.toString())
				.param("to", shiftDate.toString())
				.header(HttpHeaders.AUTHORIZATION, applicant.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(shiftId))
				.andExpect(jsonPath("$[0].workerName").value("이진호"));

		mockMvc.perform(get("/stores/{storeId}/summary", storeId)
				.header(HttpHeaders.AUTHORIZATION, owner.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pendingApprovalCount").value(0));

		mockMvc.perform(get("/me/notifications")
				.header(HttpHeaders.AUTHORIZATION, applicant.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("CONFIRMED"))
				.andExpect(jsonPath("$.content[0].requestId").value(requestId));

		mockMvc.perform(put("/me/notifications/read-all")
				.header(HttpHeaders.AUTHORIZATION, applicant.bearer()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/me/notifications")
				.param("unreadOnly", "true")
				.header(HttpHeaders.AUTHORIZATION, applicant.bearer()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0))
				.andExpect(jsonPath("$.content.length()").value(0));
	}

	private Session signUpAndLogin(String phone, String name) throws Exception {
		MvcResult signup = mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"%s","phone":"%s","password":"password123"}
						""".formatted(name, phone)))
				.andExpect(status().isCreated())
				.andReturn();
		Long userId = jsonLong(signup, "$.id");

		MvcResult login = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"phone":"%s","password":"password123"}
						""".formatted(phone)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andReturn();
		String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
		return new Session(userId, token);
	}

	private void joinAndActivate(Long storeId, String inviteCode, Session worker, Session owner) throws Exception {
		mockMvc.perform(post("/stores/join")
				.header(HttpHeaders.AUTHORIZATION, worker.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"inviteCode\":\"" + inviteCode + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"));

		mockMvc.perform(put("/stores/{storeId}/members/{userId}/status", storeId, worker.userId())
				.header(HttpHeaders.AUTHORIZATION, owner.bearer())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ACTIVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	private Long jsonLong(MvcResult result, String path) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
	}

	private record Session(Long userId, String token) {
		String bearer() {
			return "Bearer " + token;
		}
	}
}
