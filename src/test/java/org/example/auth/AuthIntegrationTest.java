package org.example.auth;

import org.example.budget.BudgetRepository;
import org.example.category.CategoryRepository;
import org.example.expense.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ExpenseRepository expenses;
    @Autowired
    private BudgetRepository budgets;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        expenses.deleteAll();
        budgets.deleteAll();
        categories.deleteAll();
        users.deleteAll();
    }

    @Test
    void registersHashesPasswordsAndRejectsDuplicatesAndInvalidInput() throws Exception {
        register("alice");
        AppUser user = users.findByUsername("alice").orElseThrow();
        assertNotEquals("password123", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("password123", user.getPasswordHash()));
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        String longPassword = "я".repeat(40);
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"other\",\"password\":\"" + longPassword + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthenticationAndCsrfAndSupportsLoginAndLogout() throws Exception {
        mvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
        register("alice");
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice").param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        MockHttpSession session = login("alice");
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/categories").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(categoryJson()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsCsrfTokenFromPublicEndpoint() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        String header = mapper.readTree(result.getResponse().getContentAsString()).get("headerName").asText();
        mvc.perform(post("/api/auth/register")
                        .session((MockHttpSession) result.getRequest().getSession(false))
                        .header(header, token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void isolatesAllResourcesAndStatisticsBetweenUsers() throws Exception {
        register("alice");
        register("bob");
        MockHttpSession alice = login("alice");
        MockHttpSession bob = login("bob");
        long budgetId = create("/api/budgets", budgetJson(), alice);
        long categoryId = create("/api/categories", categoryJson(), alice);
        String expenseJson = "{\"amount\":25.50,\"date\":\"2026-11-17\",\"budgetId\":" + budgetId
                + ",\"categoryId\":" + categoryId + "}";
        long expenseId = create("/api/expenses", expenseJson, alice);

        mvc.perform(get("/api/statistics/overview").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBudget").value(100))
                .andExpect(jsonPath("$.totalExpenses").value(25.5));
        mvc.perform(get("/api/statistics/overview").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBudget").value(0))
                .andExpect(jsonPath("$.totalExpenses").value(0));
        mvc.perform(get("/api/statistics/by-category").session(bob))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/statistics/daily").session(bob))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        for (String path : new String[]{"/api/budgets", "/api/categories", "/api/expenses"}) {
            mvc.perform(get(path).session(bob)).andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        String[] paths = {"/api/budgets/" + budgetId, "/api/categories/" + categoryId,
                "/api/expenses/" + expenseId};
        String[] bodies = {budgetJson(), categoryJson(), expenseJson};
        for (int index = 0; index < paths.length; index++) {
            mvc.perform(get(paths[index]).session(bob)).andExpect(status().isNotFound());
            mvc.perform(put(paths[index]).session(bob).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(bodies[index]))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(paths[index]).session(bob).with(csrf())).andExpect(status().isNotFound());
        }
        for (String path : new String[]{"/api/expenses", "/api/statistics/overview",
                "/api/statistics/by-category", "/api/statistics/daily"}) {
            mvc.perform(get(path).param("budgetId", Long.toString(budgetId)).session(bob))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/expenses").session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(expenseJson))
                .andExpect(status().isNotFound());
        long bobBudget = create("/api/budgets", budgetJson(), bob);
        mvc.perform(post("/api/expenses").session(bob).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson.replace("\"budgetId\":" + budgetId, "\"budgetId\":" + bobBudget)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/expenses/" + expenseId).session(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.budget.user").doesNotExist())
                .andExpect(jsonPath("$.category.user").doesNotExist());
    }

    @Test
    void hidesLegacyRecordsWithoutOwner() throws Exception {
        org.example.category.Category category = new org.example.category.Category();
        category.setName("Legacy");
        category.setColor("red");
        categories.save(category);
        register("alice");
        MockHttpSession alice = login("alice");
        mvc.perform(get("/api/categories").session(alice))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/categories/" + category.getId()).session(alice))
                .andExpect(status().isNotFound());
    }

    private void register(String username) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", username).param("password", "password123"))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long create(String path, String json, MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(post(path).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String budgetJson() {
        return "{\"name\":\"November\",\"amount\":100,\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\"}";
    }

    private String categoryJson() {
        return "{\"name\":\"Food\",\"color\":\"red\"}";
    }
}
