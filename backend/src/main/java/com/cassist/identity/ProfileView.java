package com.cassist.identity;

import java.time.Instant;
import java.util.List;

public record ProfileView(String username, String displayName, String nickname, String bio,
                          String avatarUrl, String aiReplyStyle, Instant updatedAt,
                          List<String> classNames, String studentNumber, boolean tinoUnlocked) {
    public ProfileView { classNames = classNames == null ? List.of() : List.copyOf(classNames); }
    public ProfileView(String username, String displayName, String nickname, String bio,
                       String avatarUrl, String aiReplyStyle, Instant updatedAt, List<String> classNames, String studentNumber) {
        this(username, displayName, nickname, bio, avatarUrl, aiReplyStyle, updatedAt, classNames, studentNumber, false);
    }
    public ProfileView(String username, String displayName, String nickname, String bio,
                       String avatarUrl, String aiReplyStyle, Instant updatedAt) {
        this(username, displayName, nickname, bio, avatarUrl, aiReplyStyle, updatedAt, List.of(), null);
    }
}
