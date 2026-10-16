package com.todo;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.*;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Title must not be empty")
    @Size(max = 150, message = "Title must be at most 150 characters")
    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private LocalDate deadline;

    @Pattern(regexp = "Low|Medium|High", message = "Priority must be Low, Medium or High")
    @Column(nullable = false, length = 10)
    private String priority = "Medium";

    @Pattern(regexp = "Pending|Completed", message = "Status must be Pending or Completed")
    @Column(nullable = false, length = 10)
    private String status = "Pending";

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate v) { deadline = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { priority = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** Derived field (not stored): a Pending task whose deadline has passed. */
    public boolean isOverdue() {
        return "Pending".equals(status) && deadline != null && deadline.isBefore(LocalDate.now());
    }
}
