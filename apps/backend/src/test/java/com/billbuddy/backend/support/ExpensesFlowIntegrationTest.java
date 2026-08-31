package com.billbuddy.backend.support;

import com.billbuddy.backend.features.auth.dto.request.LoginRequest;
import com.billbuddy.backend.features.auth.dto.request.SignupRequest;
import com.billbuddy.backend.features.expenses.dto.request.CreateExpenseRequest;
import com.billbuddy.backend.features.expenses.dto.request.ExpenseItemEntry;
import com.billbuddy.backend.features.expenses.dto.request.ItemAssignmentEntry;
import com.billbuddy.backend.features.expenses.dto.request.PayerEntry;
import com.billbuddy.backend.features.expenses.dto.request.PercentageEntry;
import com.billbuddy.backend.features.expenses.dto.request.UpdateExpenseRequest;
import com.billbuddy.backend.features.expenses.model.SplitType;
import com.billbuddy.backend.features.groups.dto.request.CreateGroupRequest;
import com.billbuddy.backend.features.groups.dto.request.JoinInviteRequest;
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
class ExpensesFlowIntegrationTest {

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

    @Test
    void expenseLifecycle_equalItemizedEditAndDelete() throws Exception {
        LoggedInUser admin = signupAndLogin("admin", "device-admin");
        LoggedInUser member1 = signupAndLogin("member1", "device-member1");
        LoggedInUser member2 = signupAndLogin("member2", "device-member2");

        long groupId = createGroup(admin.token());
        joinGroupViaLink(groupId, admin.token(), member1.token());
        joinGroupViaLink(groupId, admin.token(), member2.token());

        // ---- EQUAL expense: 100 paid by admin, split 3 ways ----
        CreateExpenseRequest equalRequest = new CreateExpenseRequest();
        equalRequest.setDescription("Dinner");
        equalRequest.setAmount(new BigDecimal("100"));
        equalRequest.setCurrency("INR");
        equalRequest.setSplitType(SplitType.EQUAL);

        PayerEntry payer = new PayerEntry();
        payer.setUserId(admin.userId());
        payer.setAmountPaid(new BigDecimal("100"));
        equalRequest.setPayers(List.of(payer));
        equalRequest.setParticipantUserIds(List.of(admin.userId(), member1.userId(), member2.userId()));

        MvcResult equalResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(equalRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splits.length()").value(3))
                .andReturn();

        JsonNode equalExpense = objectMapper.readTree(equalResult.getResponse().getContentAsString());
        long equalExpenseId = equalExpense.get("id").asLong();
        BigDecimal splitSum = sumSplits(equalExpense);
        assertThat(splitSum).isEqualByComparingTo("100.00");

        // ---- ITEMIZED expense: pizza (450, admin+member1) + coke (90, member2) = 540, paid by admin ----
        CreateExpenseRequest itemizedRequest = new CreateExpenseRequest();
        itemizedRequest.setDescription("Restaurant bill");
        itemizedRequest.setAmount(new BigDecimal("540"));
        itemizedRequest.setCurrency("INR");
        itemizedRequest.setSplitType(SplitType.ITEMIZED);

        PayerEntry itemizedPayer = new PayerEntry();
        itemizedPayer.setUserId(admin.userId());
        itemizedPayer.setAmountPaid(new BigDecimal("540"));
        itemizedRequest.setPayers(List.of(itemizedPayer));

        ExpenseItemEntry pizza = new ExpenseItemEntry();
        pizza.setName("Pizza");
        pizza.setAmount(new BigDecimal("450"));
        ItemAssignmentEntry pizzaAdmin = new ItemAssignmentEntry();
        pizzaAdmin.setUserId(admin.userId());
        pizzaAdmin.setShare(BigDecimal.ONE);
        ItemAssignmentEntry pizzaMember1 = new ItemAssignmentEntry();
        pizzaMember1.setUserId(member1.userId());
        pizzaMember1.setShare(BigDecimal.ONE);
        pizza.setAssignments(List.of(pizzaAdmin, pizzaMember1));

        ExpenseItemEntry coke = new ExpenseItemEntry();
        coke.setName("Coke");
        coke.setAmount(new BigDecimal("90"));
        ItemAssignmentEntry cokeMember2 = new ItemAssignmentEntry();
        cokeMember2.setUserId(member2.userId());
        cokeMember2.setShare(BigDecimal.ONE);
        coke.setAssignments(List.of(cokeMember2));

        itemizedRequest.setItems(List.of(pizza, coke));

        MvcResult itemizedResult = mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemizedRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        JsonNode itemizedExpense = objectMapper.readTree(itemizedResult.getResponse().getContentAsString());
        long itemizedExpenseId = itemizedExpense.get("id").asLong();
        assertThat(sumSplits(itemizedExpense)).isEqualByComparingTo("540.00");
        // member2 only had coke (90), never touched the pizza
        assertThat(splitFor(itemizedExpense, member2.userId())).isEqualByComparingTo("90.00");

        // ---- edit the EQUAL expense: switch it to a PERCENTAGE split ----
        UpdateExpenseRequest updateRequest = new UpdateExpenseRequest();
        updateRequest.setDescription("Dinner (updated)");
        updateRequest.setAmount(new BigDecimal("100"));
        updateRequest.setCurrency("INR");
        updateRequest.setSplitType(SplitType.PERCENTAGE);
        updateRequest.setPayers(List.of(payer));

        PercentageEntry pct1 = new PercentageEntry();
        pct1.setUserId(admin.userId());
        pct1.setPercentage(new BigDecimal("50"));
        PercentageEntry pct2 = new PercentageEntry();
        pct2.setUserId(member1.userId());
        pct2.setPercentage(new BigDecimal("30"));
        PercentageEntry pct3 = new PercentageEntry();
        pct3.setUserId(member2.userId());
        pct3.setPercentage(new BigDecimal("20"));
        updateRequest.setPercentages(List.of(pct1, pct2, pct3));

        MvcResult updateResult = mockMvc.perform(put("/api/v1/expenses/" + equalExpenseId)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitType").value("PERCENTAGE"))
                .andReturn();

        JsonNode updatedExpense = objectMapper.readTree(updateResult.getResponse().getContentAsString());
        assertThat(sumSplits(updatedExpense)).isEqualByComparingTo("100.00");

        // ---- member2 (not creator, not admin) cannot edit admin's expense ----
        mockMvc.perform(put("/api/v1/expenses/" + equalExpenseId)
                        .header("Authorization", "Bearer " + member2.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // ---- delete the itemized expense, confirm 404 afterward ----
        mockMvc.perform(delete("/api/v1/expenses/" + itemizedExpenseId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/expenses/" + itemizedExpenseId)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNotFound());

        // ---- group expense list now shows only the (updated) equal/percentage expense ----
        mockMvc.perform(get("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void createExpense_rejectsMismatchedSplitsAndCurrency() throws Exception {
        LoggedInUser admin = signupAndLogin("admin", "device-admin");
        long groupId = createGroup(admin.token());

        PayerEntry payer = new PayerEntry();
        payer.setUserId(admin.userId());
        payer.setAmountPaid(new BigDecimal("100"));

        // percentages don't sum to 100
        CreateExpenseRequest badPercentage = new CreateExpenseRequest();
        badPercentage.setDescription("Bad split");
        badPercentage.setAmount(new BigDecimal("100"));
        badPercentage.setCurrency("INR");
        badPercentage.setSplitType(SplitType.PERCENTAGE);
        badPercentage.setPayers(List.of(payer));
        PercentageEntry onlyHalf = new PercentageEntry();
        onlyHalf.setUserId(admin.userId());
        onlyHalf.setPercentage(new BigDecimal("50"));
        badPercentage.setPercentages(List.of(onlyHalf));

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPercentage)))
                .andExpect(status().isBadRequest());

        // currency mismatch with the group's default currency
        CreateExpenseRequest wrongCurrency = new CreateExpenseRequest();
        wrongCurrency.setDescription("Wrong currency");
        wrongCurrency.setAmount(new BigDecimal("100"));
        wrongCurrency.setCurrency("USD");
        wrongCurrency.setSplitType(SplitType.EQUAL);
        wrongCurrency.setPayers(List.of(payer));
        wrongCurrency.setParticipantUserIds(List.of(admin.userId()));

        mockMvc.perform(post("/api/v1/groups/" + groupId + "/expenses")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrency)))
                .andExpect(status().isBadRequest());
    }

    private BigDecimal sumSplits(JsonNode expense) {
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode split : expense.get("splits")) {
            sum = sum.add(new BigDecimal(split.get("amountOwed").asText()));
        }
        return sum;
    }

    private BigDecimal splitFor(JsonNode expense, long userId) {
        for (JsonNode split : expense.get("splits")) {
            if (split.get("userId").asLong() == userId) {
                return new BigDecimal(split.get("amountOwed").asText());
            }
        }
        throw new AssertionError("No split found for userId " + userId);
    }
}
