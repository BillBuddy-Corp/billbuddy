package com.billbuddy.backend.support;

import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
import com.billbuddy.backend.features.settlements.dto.request.CreateSettlementRequest;
import com.billbuddy.backend.features.settlements.dto.request.UpdateSettlementRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementsFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record LoggedInUser(Long userId, String token) {
    }

    private LoggedInUser signupAndLogin(String emailPrefix, String deviceId) throws Exception {
        String email = emailPrefix + "-" + System.nanoTime() + "@example.com";

        SignupRequest signup = new SignupRequest();
        signup.setFullName("Test User");
        signup.setEmail(email);
        signup.setPassword("password123");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");
        login.setDeviceId(deviceId);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new LoggedInUser(body.get("userId").asLong(), body.get("accessToken").asText());
    }

    private long createGroup(String token) throws Exception {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Goa Trip");
        request.setDefaultCurrency("INR");

        MvcResult result = mockMvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void joinGroupViaLink(long groupId, String adminToken, String joinerToken) throws Exception {
        MvcResult linkResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/invites/link/generate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(linkResult.getResponse().getContentAsString()).get("token").asText();

        JoinInviteRequest joinRequest = new JoinInviteRequest();
        joinRequest.setToken(token);
        mockMvc.perform(post("/api/v1/invites/join")
                        .header("Authorization", "Bearer " + joinerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(joinRequest)))
                .andExpect(status().isOk());
    }

    private void addEqualExpense(long groupId, LoggedInUser payer, BigDecimal amount, List<Long> participantIds, String token) throws Exception {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Trip costs");
        request.setAmount(amount);
        request.setCurrency("INR");
        request.setSplitType(SplitType.EQUAL);

        PayerEntry payerEntry = new PayerEntry();
        payerEntry.setUserId(payer.userId());
        payerEntry.setAmountPaid(amount);
        request.setPayers(List.of(payerEntry));
        request.setParticipantUserIds(participantIds);

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private BigDecimal balanceFor(JsonNode balances, long userId) {
        for (JsonNode b : balances) {
            if (b.get("userId").asLong() == userId) {
                return new BigDecimal(b.get("netBalance").asText());
            }
        }
        throw new AssertionError("No balance found for userId " + userId);
    }

    @Test
    void settlementLifecycle_balancesReflectCreateEditAndDelete() throws Exception {
        LoggedInUser admin = signupAndLogin("admin", "device-admin");
        LoggedInUser member1 = signupAndLogin("member1", "device-member1");
        LoggedInUser member2 = signupAndLogin("member2", "device-member2");

        long groupId = createGroup(admin.token());
        joinGroupViaLink(groupId, admin.token(), member1.token());
        joinGroupViaLink(groupId, admin.token(), member2.token());

        // admin pays 300, split equally 3 ways -> admin +200, member1 -100, member2 -100
        addEqualExpense(groupId, admin, new BigDecimal("300"),
                List.of(admin.userId(), member1.userId(), member2.userId()), admin.token());

        MvcResult rawBefore = mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andReturn();
        JsonNode balancesBefore = objectMapper.readTree(rawBefore.getResponse().getContentAsString());
        assertThat(balanceFor(balancesBefore, admin.userId())).isEqualByComparingTo("200.00");
        assertThat(balanceFor(balancesBefore, member1.userId())).isEqualByComparingTo("-100.00");
        assertThat(balanceFor(balancesBefore, member2.userId())).isEqualByComparingTo("-100.00");

        MvcResult simplifiedBefore = mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances/simplified")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode simplifiedBeforeBody = objectMapper.readTree(simplifiedBefore.getResponse().getContentAsString());
        for (JsonNode transfer : simplifiedBeforeBody) {
            assertThat(transfer.get("toUserId").asLong()).isEqualTo(admin.userId());
        }

        // member1 pays admin 100 in cash -> fully settles member1's debt
        CreateSettlementRequest createSettlement = new CreateSettlementRequest();
        createSettlement.setPaidByUserId(member1.userId());
        createSettlement.setPaidToUserId(admin.userId());
        createSettlement.setAmount(new BigDecimal("100"));
        createSettlement.setCurrency("INR");
        createSettlement.setNote("Cash at the airport");

        MvcResult settlementResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + member1.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createSettlement)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paidByUserId").value(member1.userId()))
                .andExpect(jsonPath("$.paidToUserId").value(admin.userId()))
                .andReturn();
        long settlementId = objectMapper.readTree(settlementResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult rawAfterSettle = mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode balancesAfterSettle = objectMapper.readTree(rawAfterSettle.getResponse().getContentAsString());
        assertThat(balanceFor(balancesAfterSettle, admin.userId())).isEqualByComparingTo("100.00");
        assertThat(balanceFor(balancesAfterSettle, member1.userId())).isEqualByComparingTo("0.00");
        assertThat(balanceFor(balancesAfterSettle, member2.userId())).isEqualByComparingTo("-100.00");

        // simplification should now need only one transfer: member2 -> admin
        mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances/simplified")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromUserId").value(member2.userId()))
                .andExpect(jsonPath("$[0].toUserId").value(admin.userId()))
                .andExpect(jsonPath("$[0].amount").value(100.0));

        // ---- edit the settlement: correct the amount down to 50 ----
        UpdateSettlementRequest updateSettlement = new UpdateSettlementRequest();
        updateSettlement.setPaidByUserId(member1.userId());
        updateSettlement.setPaidToUserId(admin.userId());
        updateSettlement.setAmount(new BigDecimal("50"));
        updateSettlement.setCurrency("INR");
        updateSettlement.setNote("Correction: only paid half");

        mockMvc.perform(put("/api/v1/settlements/" + settlementId)
                        .header("Authorization", "Bearer " + member1.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateSettlement)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(50.0));

        MvcResult rawAfterEdit = mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode balancesAfterEdit = objectMapper.readTree(rawAfterEdit.getResponse().getContentAsString());
        assertThat(balanceFor(balancesAfterEdit, admin.userId())).isEqualByComparingTo("150.00");
        assertThat(balanceFor(balancesAfterEdit, member1.userId())).isEqualByComparingTo("-50.00");
        assertThat(balanceFor(balancesAfterEdit, member2.userId())).isEqualByComparingTo("-100.00");

        // ---- member2 (not the logger, not admin) cannot edit member1's settlement ----
        mockMvc.perform(put("/api/v1/settlements/" + settlementId)
                        .header("Authorization", "Bearer " + member2.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateSettlement)))
                .andExpect(status().isForbidden());

        // ---- delete the settlement: balances revert to pre-settlement state ----
        mockMvc.perform(delete("/api/v1/settlements/" + settlementId)
                        .header("Authorization", "Bearer " + member1.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/settlements/" + settlementId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNotFound());

        MvcResult rawAfterDelete = mockMvc.perform(get("/api/v1/groups/" + groupId + "/balances")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode balancesAfterDelete = objectMapper.readTree(rawAfterDelete.getResponse().getContentAsString());
        assertThat(balanceFor(balancesAfterDelete, admin.userId())).isEqualByComparingTo("200.00");
        assertThat(balanceFor(balancesAfterDelete, member1.userId())).isEqualByComparingTo("-100.00");
        assertThat(balanceFor(balancesAfterDelete, member2.userId())).isEqualByComparingTo("-100.00");
    }

    @Test
    void createSettlement_rejectsSamePartyAndCurrencyMismatch() throws Exception {
        LoggedInUser admin = signupAndLogin("admin", "device-admin");
        long groupId = createGroup(admin.token());

        CreateSettlementRequest samePerson = new CreateSettlementRequest();
        samePerson.setPaidByUserId(admin.userId());
        samePerson.setPaidToUserId(admin.userId());
        samePerson.setAmount(new BigDecimal("10"));
        samePerson.setCurrency("INR");

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samePerson)))
                .andExpect(status().isBadRequest());

        LoggedInUser member1 = signupAndLogin("member1", "device-member1");
        joinGroupViaLink(groupId, admin.token(), member1.token());

        CreateSettlementRequest wrongCurrency = new CreateSettlementRequest();
        wrongCurrency.setPaidByUserId(member1.userId());
        wrongCurrency.setPaidToUserId(admin.userId());
        wrongCurrency.setAmount(new BigDecimal("10"));
        wrongCurrency.setCurrency("USD");

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/settlements")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrency)))
                .andExpect(status().isBadRequest());
    }
}
