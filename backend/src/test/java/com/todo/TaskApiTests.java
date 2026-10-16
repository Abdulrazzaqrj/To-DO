package com.todo;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskApiTests {
    @Autowired MockMvc mvc;
    @Autowired TaskRepository repo;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() { repo.deleteAll(); }

    private long create(String title, String desc, LocalDate deadline, String priority) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", title);
        body.put("description", desc);
        body.put("deadline", deadline == null ? null : deadline.toString());
        body.put("priority", priority);
        String res = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asLong();
    }

    @Test
    void createReturnsPendingTask() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Buy milk\",\"description\":\"2 litres\",\"deadline\":\"2030-01-01\",\"priority\":\"High\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("Buy milk"))
            .andExpect(jsonPath("$.status").value("Pending"))
            .andExpect(jsonPath("$.priority").value("High"))
            .andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void emptyTitleIsRejected() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Title must not be empty"));
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Title must not be empty"));
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidPriorityAndBadDateAreRejected() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"priority\":\"Urgent\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Priority must be Low, Medium or High"));
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"deadline\":\"not-a-date\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void listAndGetById() throws Exception {
        long id = create("A", "first", LocalDate.now().plusDays(1), "Low");
        create("B", "second", null, "Medium");
        mvc.perform(get("/api/tasks")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/tasks/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("A"));
        mvc.perform(get("/api/tasks/999999")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Task not found"));
    }

    @Test
    void updateChangesFieldsAndKeepsStatus() throws Exception {
        long id = create("Old", "d", null, "Low");
        mvc.perform(patch("/api/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Completed\"}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/tasks/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  New title \",\"description\":\"changed\",\"deadline\":\"2030-05-05\",\"priority\":\"High\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("New title"))
            .andExpect(jsonPath("$.description").value("changed"))
            .andExpect(jsonPath("$.deadline").value("2030-05-05"))
            .andExpect(jsonPath("$.priority").value("High"))
            .andExpect(jsonPath("$.status").value("Completed"));
        mvc.perform(put("/api/tasks/999999").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/tasks/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void statusCanBeToggledAndValidated() throws Exception {
        long id = create("T", null, null, "Medium");
        mvc.perform(patch("/api/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Completed\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Completed"));
        mvc.perform(patch("/api/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Pending\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("Pending"));
        mvc.perform(patch("/api/tasks/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Done\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Status must be Pending or Completed"));
    }

    @Test
    void filterByStatus() throws Exception {
        long a = create("pending one", null, null, "Low");
        long b = create("done one", null, null, "Low");
        mvc.perform(patch("/api/tasks/" + b + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Completed\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/tasks").param("status", "Pending")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value((int) a));
        mvc.perform(get("/api/tasks").param("status", "Completed")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value((int) b));
        mvc.perform(get("/api/tasks").param("status", "All")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void searchMatchesTitleAndDescriptionCaseInsensitive() throws Exception {
        create("Write report", "quarterly numbers", null, "Medium");
        create("Gym", "leg day", null, "Medium");
        mvc.perform(get("/api/tasks").param("search", "REPORT")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/tasks").param("search", "leg")).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].title").value("Gym"));
        mvc.perform(get("/api/tasks").param("search", "zzz")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void overdueOnlyIncludesPendingTasksPastDeadline() throws Exception {
        long late = create("late", null, LocalDate.now().minusDays(2), "High");
        long doneLate = create("done late", null, LocalDate.now().minusDays(2), "High");
        create("future", null, LocalDate.now().plusDays(2), "High");
        mvc.perform(patch("/api/tasks/" + doneLate + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"Completed\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/tasks").param("overdue", "true"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value((int) late))
            .andExpect(jsonPath("$[0].overdue").value(true));
    }

    @Test
    void deleteRemovesTask() throws Exception {
        long id = create("bye", null, null, "Low");
        mvc.perform(delete("/api/tasks/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsFrontendOrigin() throws Exception {
        mvc.perform(options("/api/tasks")
                .header("Origin", "http://localhost:5500")
                .header("Access-Control-Request-Method", "PATCH"))
            .andExpect(status().isOk())
            .andExpect(header().exists("Access-Control-Allow-Origin"));
    }
}
