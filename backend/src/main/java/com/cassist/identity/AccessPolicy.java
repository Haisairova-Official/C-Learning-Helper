package com.cassist.identity;

import com.cassist.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccessPolicy {
    private final JdbcTemplate jdbc;
    public AccessPolicy(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void requireManager(Actor actor) {
        if (!actor.canManageProblems()) forbidden();
    }

    public void requireOwner(Actor actor, String ownerId) {
        if (actor.role() != Role.ADMIN && !actor.id().equals(ownerId)) forbidden();
    }

    public void requireClassManage(Actor actor, String classId) {
        requireManager(actor);
        var owners = jdbc.queryForList("SELECT teacher_id FROM classroom WHERE id = ?", String.class, classId);
        if (owners.isEmpty()) notFound();
        requireOwner(actor, owners.getFirst());
    }

    public void requireAssignmentManage(Actor actor, String assignmentId) {
        requireManager(actor);
        var owners = jdbc.queryForList("SELECT owner_id FROM assignment WHERE id = ?", String.class, assignmentId);
        if (owners.isEmpty()) notFound();
        requireOwner(actor, owners.getFirst());
    }

    public void requireAssignmentRead(Actor actor, String assignmentId) {
        if (actor.role() != Role.STUDENT) { requireAssignmentManage(actor, assignmentId); return; }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assignment_class ac JOIN class_member cm ON cm.class_id=ac.class_id WHERE ac.assignment_id=? AND cm.student_id=?", Integer.class, assignmentId, actor.id());
        if (count == null || count == 0) forbidden();
    }

    public void requireSubmissionRead(Actor actor, String studentId, String assignmentId) {
        if (actor.role() == Role.STUDENT) {
            if (!actor.id().equals(studentId)) forbidden();
            requireAssignmentRead(actor, assignmentId);
        } else requireAssignmentManage(actor, assignmentId);
    }

    public static void forbidden() { throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该资源"); }
    public static void notFound() { throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在"); }
}
