package com.cassist.identity;

import com.cassist.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class CurrentActor {
    public Actor require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Actor actor) return actor;
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
    }

    public Actor requireProblemManager() {
        Actor actor = require();
        if (!actor.canManageProblems()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "需要题目管理权限");
        }
        return actor;
    }
}
