package com.cassist.identity;

import com.cassist.common.*;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserManagementService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AvatarNormalizer avatars;
    public UserManagementService(JdbcTemplate jdbc, PasswordEncoder passwords, AvatarNormalizer avatars) {
        this.jdbc=jdbc; this.passwords=passwords; this.avatars=avatars;
    }
    public record UserView(String id,String username,String displayName,Role role,String studentNumber,String nickname,String bio,
                           AiReplyStyle aiReplyStyle,boolean enabled,String avatarUrl,boolean tinoUnlocked) {}
    public record Create(String username,String displayName,Role role,String studentNumber,String password) {
        @Override public String toString() { return "Create[credentials omitted]"; }
    }
    public record Edit(String displayName,String studentNumber,String nickname,String bio,AiReplyStyle aiReplyStyle) {}
    public static void requireAdmin(Actor actor) { if(actor.role()!=Role.ADMIN) AccessPolicy.forbidden(); }
    public static String text(String value) { return value==null?"":value.strip(); }
    public static void invalid(String message) { throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_USER",message); }
    public static void validateName(String name) {
        if(text(name).isEmpty() || name.codePointCount(0,name.length())>100 || name.chars().anyMatch(Character::isISOControl)) invalid("姓名需为 1 至 100 个字符");
    }
    public static void validateNumber(String number) {
        if(!text(number).isEmpty() && !number.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,31}")) invalid("学号需为不超过 32 位的字母、数字、下划线或连字符");
    }
    public static void validateUsername(String username) {
        if(!text(username).matches("[A-Za-z0-9][A-Za-z0-9_.-]{2,99}")) invalid("账号需为 3 至 100 位字母、数字、点、下划线或连字符");
    }
    public static void validatePassword(String password) {
        if(password==null || password.length()<8 || password.getBytes(StandardCharsets.UTF_8).length>72 || !password.matches("(?s).*[A-Za-z].*") || !password.matches("(?s).*[0-9].*")) invalid("初始密码需包含字母和数字，至少 8 位且不超过 72 字节");
    }
    private UserView map(ResultSet rs,int index) throws SQLException {
        String id=rs.getString("id");
        var time=rs.getObject("profile_updated_at",LocalDateTime.class);
        return new UserView(id,rs.getString("username"),rs.getString("display_name"),Role.valueOf(rs.getString("role")),rs.getString("student_number"),
                rs.getString("nickname"),rs.getString("bio"),AiReplyStyle.valueOf(rs.getString("ai_reply_style")),rs.getBoolean("enabled"),
                rs.getBoolean("has_avatar")?"/api/admin/users/"+id+"/avatar?v="+time.toEpochSecond(ZoneOffset.UTC)+"-"+time.getNano():null,rs.getBoolean("tino_unlocked"));
    }
    private static final String COLUMNS="id,username,display_name,role,student_number,nickname,bio,CASE WHEN tino_enabled=TRUE THEN 'TINO' ELSE ai_reply_style END AS ai_reply_style,tino_unlocked,enabled,profile_updated_at,avatar_png IS NOT NULL AS has_avatar";
    public PageResult<UserView> list(Actor actor,int page,int size,String query) {
        requireAdmin(actor);
        if(page<1 || size<1 || size>100 || text(query).length()>100) invalid("查询范围不正确");
        String q=text(query); String where=" WHERE LOCATE(?,username)>0 OR LOCATE(?,display_name)>0 OR LOCATE(?,COALESCE(student_number,''))>0";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM app_user"+where,Long.class,q,q,q);
        return new PageResult<>(jdbc.query("SELECT "+COLUMNS+" FROM app_user"+where+" ORDER BY created_at DESC,id LIMIT ? OFFSET ?",this::map,q,q,q,size,(long)(page-1)*size),page,size,total);
    }
    public UserView get(Actor actor,String id) {
        requireAdmin(actor);
        var rows=jdbc.query("SELECT "+COLUMNS+" FROM app_user WHERE id=?",this::map,id);
        if(rows.isEmpty()) AccessPolicy.notFound();
        return rows.getFirst();
    }
    public void checkUnique(String username,String number,String exceptId) {
        if(!text(username).isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=? AND id<>?",Integer.class,username,exceptId)>0) invalid("登录账号已存在");
        if(!text(number).isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE student_number=? AND id<>?",Integer.class,number,exceptId)>0) invalid("学号已存在");
    }
    @Transactional public UserView create(Actor actor,Create input) {
        requireAdmin(actor); if(input==null || input.role()==null) invalid("请选择角色");
        String username=text(input.username()),name=text(input.displayName()),number=text(input.studentNumber());
        validateUsername(username); validateName(name); validateNumber(number); validatePassword(input.password());
        if(input.role()!=Role.STUDENT && !number.isEmpty()) invalid("只有学生账号可以填写学号");
        checkUnique(username,number,"");
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app_user(id,username,display_name,password_hash,role,student_number,created_at) VALUES(?,?,?,?,?,?,?)",id,username,name,passwords.encode(input.password()),input.role().name(),number.isEmpty()?null:number,LocalDateTime.now(ZoneOffset.UTC));
        return get(actor,id);
    }
    @Transactional public UserView edit(Actor actor,String id,Edit input) {
        UserView previous=get(actor,id);
        if(input==null || input.aiReplyStyle()==null) invalid("请选择 AI 回复风格");
        String name=text(input.displayName()),number=text(input.studentNumber()),nickname=text(input.nickname()),bio=input.bio()==null?"":input.bio();
        validateName(name); validateNumber(number);
        if(previous.role()!=Role.STUDENT && !number.isEmpty()) invalid("只有学生账号可以填写学号");
        if(nickname.codePointCount(0,nickname.length())>32 || bio.codePointCount(0,bio.length())>500) invalid("昵称最多 32 字，简介最多 500 字");
        if(input.aiReplyStyle()==AiReplyStyle.TINO && !previous.tinoUnlocked()) throw new BusinessException(HttpStatus.FORBIDDEN,"TINO_LOCKED","该账号尚未解锁小侦探 Tino");
        checkUnique("",number,id);
        jdbc.update("UPDATE app_user SET display_name=?,student_number=?,nickname=?,bio=?,ai_reply_style=?,tino_enabled=?,profile_updated_at=? WHERE id=?",name,number.isEmpty()?null:number,nickname,bio,input.aiReplyStyle()==AiReplyStyle.TINO?"GUIDED":input.aiReplyStyle().name(),input.aiReplyStyle()==AiReplyStyle.TINO,LocalDateTime.now(ZoneOffset.UTC),id);
        return get(actor,id);
    }
    public byte[] avatar(Actor actor,String id) {
        get(actor,id);
        byte[] value=jdbc.queryForObject("SELECT avatar_png FROM app_user WHERE id=?",byte[].class,id);
        if(value==null) AccessPolicy.notFound(); return value;
    }
    @Transactional public UserView avatar(Actor actor,String id,MultipartFile file) {
        get(actor,id);
        byte[] png=file==null?null:avatars.normalize(file);
        jdbc.update("UPDATE app_user SET avatar_png=?,profile_updated_at=? WHERE id=?",png,LocalDateTime.now(ZoneOffset.UTC),id);
        return get(actor,id);
    }
}
