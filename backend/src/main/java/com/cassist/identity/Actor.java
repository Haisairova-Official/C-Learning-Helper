package com.cassist.identity;

import java.security.Principal;
import java.util.Objects;

public record Actor(String id, Role role) implements Principal {
    public Actor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(role);
        if (id.isBlank()) throw new IllegalArgumentException("Actor id cannot be blank");
    }

    @Override public String getName() { return id; }
    public boolean canManageProblems() { return role == Role.TEACHER || role == Role.ADMIN; }
}
