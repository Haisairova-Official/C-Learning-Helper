package com.cassist.identity;

import com.cassist.common.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;
    private final AvatarNormalizer avatars;

    public ProfileService(JdbcTemplate jdbc, AvatarNormalizer avatars) {
        this.jdbc = jdbc; this.avatars = avatars;
    }

    public ProfileView get(Actor actor) {
        var rows = jdbc.query("SELECT username,display_name,nickname,bio,CASE WHEN tino_enabled=TRUE THEN 'TINO' ELSE ai_reply_style END AS ai_reply_style,profile_updated_at,avatar_png IS NOT NULL AS has_avatar,tino_unlocked,student_number FROM app_user WHERE id=? AND enabled=TRUE",
                (rs, n) -> {
                    var updated = rs.getObject("profile_updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC);
                    long version = updated.getEpochSecond() * 1_000_000 + updated.getNano() / 1_000;
                    return new ProfileView(rs.getString("username"), rs.getString("display_name"), rs.getString("nickname"),
                            rs.getString("bio"), rs.getBoolean("has_avatar") ? "/api/profile/avatar?v=" + version : null,
                            AiReplyStyle.valueOf(rs.getString("ai_reply_style")).name(), updated, List.of(),
                            actor.role() == Role.STUDENT ? rs.getString("student_number") : null, rs.getBoolean("tino_unlocked"));
                }, actor.id());
        if (rows.isEmpty()) throw unavailable();
        var profile = rows.getFirst();
        List<String> classNames = actor.role() == Role.STUDENT
                ? jdbc.queryForList("SELECT c.name FROM classroom c JOIN class_member cm ON cm.class_id=c.id WHERE cm.student_id=? ORDER BY c.name,c.id", String.class, actor.id())
                : List.of();
        return new ProfileView(profile.username(), profile.displayName(), profile.nickname(), profile.bio(),
                profile.avatarUrl(), profile.aiReplyStyle(), profile.updatedAt(), classNames,
                actor.role() == Role.STUDENT ? profile.studentNumber() : null, profile.tinoUnlocked());
    }

    @Transactional
    public ProfileView update(Actor actor, ProfileUpdate input) {
        if (input == null || input.aiReplyStyle() == null) throw invalid("请选择有效的 AI 回复风格");
        String nickname = input.nickname() == null ? "" : input.nickname();
        String bio = input.bio() == null ? "" : input.bio();
        if (nickname.codePointCount(0, nickname.length()) > 32) throw invalid("昵称不能超过 32 个字符");
        if (bio.codePointCount(0, bio.length()) > 500) throw invalid("个人简介不能超过 500 个字符");
        requireUnlocked(actor.id(), input.aiReplyStyle());
        int changed = jdbc.update("UPDATE app_user SET nickname=?,bio=?,ai_reply_style=?,tino_enabled=?,profile_updated_at=? WHERE id=? AND enabled=TRUE",
                nickname.strip(), bio, input.aiReplyStyle()==AiReplyStyle.TINO?"GUIDED":input.aiReplyStyle().name(), input.aiReplyStyle()==AiReplyStyle.TINO, now(), actor.id());
        if (changed == 0) throw unavailable();
        return get(actor);
    }

    @Transactional
    public ProfileView uploadAvatar(Actor actor, MultipartFile file) {
        byte[] png = avatars.normalize(file);
        int changed = jdbc.update("UPDATE app_user SET avatar_png=?,profile_updated_at=? WHERE id=? AND enabled=TRUE", png, now(), actor.id());
        if (changed == 0) throw unavailable();
        return get(actor);
    }

    @Transactional
    public ProfileView deleteAvatar(Actor actor) {
        int changed = jdbc.update("UPDATE app_user SET avatar_png=NULL,profile_updated_at=? WHERE id=? AND enabled=TRUE", now(), actor.id());
        if (changed == 0) throw unavailable();
        return get(actor);
    }

    public byte[] getAvatar(Actor actor) {
        var rows = jdbc.query("SELECT avatar_png FROM app_user WHERE id=? AND enabled=TRUE", (rs, n) -> rs.getBytes(1), actor.id());
        if (rows.isEmpty()) throw unavailable();
        if (rows.getFirst() == null)
            throw new BusinessException(HttpStatus.NOT_FOUND, "AVATAR_NOT_FOUND", "尚未设置头像");
        return rows.getFirst();
    }

    /** Called only by server code using the authenticated account ID, never request-supplied prompt text. */
    public String getAiReplyStyle(String actorId) {
        var values = jdbc.queryForList("SELECT CASE WHEN tino_enabled=TRUE THEN 'TINO' ELSE ai_reply_style END FROM app_user WHERE id=? AND enabled=TRUE", String.class, actorId);
        if (values.isEmpty()) throw unavailable();
        return AiReplyStyle.valueOf(values.getFirst()).name();
    }

    /** A per-request address read from the authenticated account, never from AI request JSON. */
    public String getAiAddress(Actor actor) {
        var values = jdbc.queryForList("SELECT nickname,display_name FROM app_user WHERE id=? AND enabled=TRUE", actor.id());
        if (values.isEmpty()) throw unavailable();
        var profile = values.getFirst();
        String nickname = usableAddress(profile.get("nickname"));
        if (nickname != null) return nickname;
        String name = usableAddress(profile.get("display_name"));
        if (name != null) return name + (actor.role() == Role.STUDENT ? "同学" : "");
        return actor.role() == Role.STUDENT ? "同学" : "你";
    }

    private static String usableAddress(Object value) {
        if (!(value instanceof String name)) return null;
        String stripped = name.strip();
        if (stripped.isEmpty() || stripped.codePointCount(0, stripped.length()) > 100) return null;
        if (stripped.codePoints().anyMatch(c -> Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.LINE_SEPARATOR
                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) return null;
        return stripped;
    }

    public void requireUnlocked(String actorId, AiReplyStyle style) {
        if (style != AiReplyStyle.TINO) return;
        var values = jdbc.queryForList("SELECT tino_unlocked FROM app_user WHERE id=? AND enabled=TRUE", Boolean.class, actorId);
        if (values.isEmpty()) throw unavailable();
        if (!Boolean.TRUE.equals(values.getFirst()))
            throw new BusinessException(HttpStatus.FORBIDDEN,"TINO_LOCKED","尚未解锁小侦探 Tino");
    }

    @Transactional public ProfileView unlockTino(Actor actor,String sequence) {
        if (!"12332112323".equals(sequence)) throw invalid("未能解锁，请重新尝试");
        // A repeated unlock may report zero changed rows. Reading the enabled account
        // afterwards distinguishes that successful replay from an unavailable account.
        jdbc.update("UPDATE app_user SET tino_unlocked=TRUE WHERE id=? AND enabled=TRUE",actor.id());
        return get(actor);
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    private static BusinessException unavailable() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号不可用，请重新登录");
    }
    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", message);
    }
}
