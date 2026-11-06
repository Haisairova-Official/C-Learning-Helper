package com.cassist.identity;

import jakarta.validation.constraints.NotNull;

/** Login name, school name, role and target account ID are intentionally absent. */
public record ProfileUpdate(String nickname, String bio, @NotNull AiReplyStyle aiReplyStyle) { }
