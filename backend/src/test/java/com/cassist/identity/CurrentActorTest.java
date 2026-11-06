package com.cassist.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.cassist.common.BusinessException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorTest {
    private final CurrentActor actors = new CurrentActor();
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void rejectsAbsentOrForeignPrincipal() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, actors::require).status());
        authenticate("anonymousUser");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, actors::require).status());
    }

    @Test void studentCannotEnterManagementBoundary() {
        authenticate(new Actor("student-1", Role.STUDENT));
        assertEquals("student-1", actors.require().id());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class, actors::requireProblemManager).status());
    }

    @Test void teacherAndAdminCanEnterRoleBoundaryButStillNeedObjectChecks() {
        for (Role role : List.of(Role.TEACHER, Role.ADMIN)) {
            Actor actor = new Actor("manager-1", role);
            authenticate(actor);
            assertEquals(actor, actors.requireProblemManager());
        }
    }

    private void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_TEST"))));
    }
}
