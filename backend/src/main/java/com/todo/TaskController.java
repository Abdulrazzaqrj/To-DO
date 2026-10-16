package com.todo;

import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository repo;

    public TaskController(TaskRepository repo) { this.repo = repo; }

    // Health check endpoint for cloud platforms (Render, Railway, etc.)
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    // GET /api/tasks?status=Pending|Completed|Overdue|All&search=text&overdue=true
    @GetMapping
    public List<Task> list(@RequestParam(required = false) String status,
                           @RequestParam(required = false) String search,
                           @RequestParam(defaultValue = "false") boolean overdue) {
        Stream<Task> s = repo.findAll().stream();
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("All")) {
            if (status.equalsIgnoreCase("Overdue")) {
                s = s.filter(Task::isOverdue);
            } else {
                s = s.filter(t -> t.getStatus().equalsIgnoreCase(status));
            }
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            s = s.filter(t -> t.getTitle().toLowerCase().contains(q)
                    || (t.getDescription() != null && t.getDescription().toLowerCase().contains(q)));
        }
        if (overdue) s = s.filter(Task::isOverdue);
        Comparator<Task> byDeadline = Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()));
        return s.sorted(byDeadline.thenComparing(Task::getId, Comparator.reverseOrder())).toList();
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable Long id) { return find(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody Task in) {
        Task t = new Task();
        apply(t, in); // a new Task() starts as Pending
        return repo.save(t);
    }

    @PutMapping("/{id}")
    public Task update(@PathVariable Long id, @Valid @RequestBody Task in) {
        Task t = find(id);
        apply(t, in);
        return repo.save(t);
    }

    @PatchMapping("/{id}/status")
    public Task status(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String st = body != null ? body.get("status") : null;
        if (!"Pending".equals(st) && !"Completed".equals(st))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be Pending or Completed");
        Task t = find(id);
        t.setStatus(st);
        return repo.save(t);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { repo.delete(find(id)); }

    private Task find(Long id) {
        return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    /** Copies editable fields. Status is not touched here: it changes only via PATCH /{id}/status. */
    private void apply(Task t, Task in) {
        t.setTitle(in.getTitle().trim());
        t.setDescription(in.getDescription());
        t.setDeadline(in.getDeadline());
        if (in.getPriority() != null) t.setPriority(in.getPriority());
    }
}
