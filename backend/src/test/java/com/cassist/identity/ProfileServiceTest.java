package com.cassist.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.cassist.common.BusinessException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

class ProfileServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AvatarNormalizer avatars = mock(AvatarNormalizer.class);
    private final ProfileService service = new ProfileService(jdbc, avatars);
    private final Actor student = new Actor("student-1", Role.STUDENT);
    private final ProfileView view = new ProfileView("student", "学校姓名", "昵称", "简介", null, "GUIDED", Instant.EPOCH);

    @BeforeEach void prepare() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(student.id()))).thenReturn(List.of(view));
    }

    @Test @SuppressWarnings("unchecked") void readsOnlyOwnClassesAndIndependentNumberWithoutChangingNicknameOrAvatar() throws Exception {
        ResultSet rs=mock(ResultSet.class);
        when(rs.getString("username")).thenReturn("login-is-not-number");
        when(rs.getString("display_name")).thenReturn("学校姓名");
        when(rs.getString("nickname")).thenReturn("Test User");
        when(rs.getString("bio")).thenReturn("已有简介");
        when(rs.getString("ai_reply_style")).thenReturn("GUIDED");
        when(rs.getString("student_number")).thenReturn("20260001");
        when(rs.getBoolean("has_avatar")).thenReturn(true);
        when(rs.getObject("profile_updated_at",LocalDateTime.class)).thenReturn(LocalDateTime.of(2026,11,6,0,0));
        when(jdbc.query(contains("student_number FROM app_user"),any(RowMapper.class),eq(student.id())))
                .thenAnswer(call->List.of(((RowMapper<ProfileView>)call.getArgument(1)).mapRow(rs,0)));
        when(jdbc.queryForList(contains("JOIN class_member"),eq(String.class),eq(student.id()))).thenReturn(List.of("C 语言演示班","程序设计二班"));
        ProfileView result=service.get(student);
        assertEquals(List.of("C 语言演示班","程序设计二班"),result.classNames()); assertEquals("20260001",result.studentNumber());
        assertEquals("Test User",result.nickname()); assertEquals("学校姓名",result.displayName());
        assertEquals("login-is-not-number",result.username()); assertTrue(result.avatarUrl().startsWith("/api/profile/avatar?v="));
        verify(jdbc).queryForList(eq("SELECT c.name FROM classroom c JOIN class_member cm ON cm.class_id=c.id WHERE cm.student_id=? ORDER BY c.name,c.id"),eq(String.class),eq(student.id()));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void missingIndependentNumberNeverFallsBackToUsername() {
        ProfileView result=service.get(student);
        assertEquals("student",result.username()); assertNull(result.studentNumber()); assertEquals(List.of(),result.classNames());
    }

    @Test void managerProfileDoesNotQueryStudentClassMembership() {
        Actor teacher=new Actor("teacher-1",Role.TEACHER);
        when(jdbc.query(anyString(),any(RowMapper.class),eq(teacher.id()))).thenReturn(List.of(view));
        ProfileView result=service.get(teacher);
        assertEquals(List.of(),result.classNames()); assertNull(result.studentNumber());
        verify(jdbc,never()).queryForList(contains("class_member"),eq(String.class),anyString());
    }

    @Test void countsUnicodeCodePointsAndPreservesBioNewlines() {
        String nickname = "😀".repeat(32), bio = "🧑".repeat(499) + "\n";
        assertEquals(view, service.update(student, new ProfileUpdate(nickname, bio, AiReplyStyle.DETAILED)));
        verify(jdbc).update(contains("SET nickname=?,bio=?,ai_reply_style=?,tino_enabled=?"), eq(nickname), eq(bio), eq("DETAILED"), eq(false), any(LocalDateTime.class), eq(student.id()));
    }

    @Test void rejectsOverLimitAndMissingStyleBeforeWriting() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> service.update(student, new ProfileUpdate("😀".repeat(33), "", AiReplyStyle.GUIDED))).status());
        assertThrows(BusinessException.class, () -> service.update(student, new ProfileUpdate("", "中".repeat(501), AiReplyStyle.GUIDED)));
        assertThrows(BusinessException.class, () -> service.update(student, new ProfileUpdate("", "", null)));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void normalizesEmptyNicknameWithoutChangingSchoolIdentity() {
        service.update(student, new ProfileUpdate("  \t ", null, AiReplyStyle.CONCISE));
        verify(jdbc).update(eq("UPDATE app_user SET nickname=?,bio=?,ai_reply_style=?,tino_enabled=?,profile_updated_at=? WHERE id=? AND enabled=TRUE"),
                eq(""), eq(""), eq("CONCISE"), eq(false), any(LocalDateTime.class), eq(student.id()));
    }

    @Test void absentOrDisabledAccountCannotChangeProfile() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class,
                () -> service.update(student, new ProfileUpdate("a", "", AiReplyStyle.GUIDED))).status());
    }

    @Test void savesOnlyNormalizedAvatarForCurrentActor() {
        var file = new MockMultipartFile("file", new byte[] {1, 2, 3});
        byte[] png = new byte[] {10, 20, 30};
        when(avatars.normalize(file)).thenReturn(png);
        service.uploadAvatar(student, file);
        verify(jdbc).update(contains("SET avatar_png=?"), same(png), any(LocalDateTime.class), eq(student.id()));
    }

    @Test void distinguishesMissingAvatarFromUnavailableAccount() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(student.id()))).thenReturn(Arrays.asList((byte[]) null));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> service.getAvatar(student)).status());
        when(jdbc.query(anyString(), any(RowMapper.class), eq(student.id()))).thenReturn(List.of());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, () -> service.getAvatar(student)).status());
    }


    @Test void lockedPersonaCannotBeSelectedThroughProfileInput() {
        when(jdbc.queryForList(anyString(),eq(Boolean.class),eq(student.id()))).thenReturn(List.of(false));
        var failure=assertThrows(BusinessException.class,()->service.update(student,new ProfileUpdate("","",AiReplyStyle.TINO)));
        assertEquals("TINO_LOCKED",failure.code());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void wrongOrIncompleteUnlockSequenceDoesNotWrite() {
        for(String sequence:List.of("1233211232","123321123231","12332112322",""))
            assertThrows(BusinessException.class,()->service.unlockTino(student,sequence));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void unlockingDoesNotWriteProfileContentsOrSelectStyle() {
        service.unlockTino(student,"12332112323");
        verify(jdbc).update(eq("UPDATE app_user SET tino_unlocked=TRUE WHERE id=? AND enabled=TRUE"),eq(student.id()));
        verify(jdbc,never()).update(contains("ai_reply_style="),any(Object[].class));
    }
    @Test void repeatedUnlockSucceedsEvenWhenNoRowsChange() {
        var unlocked = new ProfileView("student", "学校姓名", "原昵称", "原简介", null, "DETAILED", Instant.EPOCH, List.of(), null, true);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
        when(jdbc.query(anyString(),any(RowMapper.class),eq(student.id()))).thenReturn(List.of(unlocked));
        assertEquals(unlocked,service.unlockTino(student,"12332112323"));
    }
    @Test void unavailableAccountCannotUnlockOrSelectPersona() {
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(0);
        when(jdbc.query(anyString(),any(RowMapper.class),eq(student.id()))).thenReturn(List.of());
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(BusinessException.class,()->service.unlockTino(student,"12332112323")).status());
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(BusinessException.class,()->service.requireUnlocked(student.id(),AiReplyStyle.TINO)).status());
    }
    @Test void unlockedPersonaCanBeSelectedAndRegularStyleDisablesIt() {
        when(jdbc.queryForList(anyString(),eq(Boolean.class),eq(student.id()))).thenReturn(List.of(true));
        service.update(student,new ProfileUpdate("原昵称","原简介",AiReplyStyle.TINO));
        verify(jdbc).update(contains("SET nickname=?,bio=?,ai_reply_style=?,tino_enabled=?"),eq("原昵称"),eq("原简介"),eq("GUIDED"),eq(true),any(LocalDateTime.class),eq(student.id()));
        service.update(student,new ProfileUpdate("原昵称","原简介",AiReplyStyle.CONCISE));
        verify(jdbc).update(contains("SET nickname=?,bio=?,ai_reply_style=?,tino_enabled=?"),eq("原昵称"),eq("原简介"),eq("CONCISE"),eq(false),any(LocalDateTime.class),eq(student.id()));
    }

    @Test void readsTrustedStyleOnlyForEnabledAccount() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq(student.id()))).thenReturn(List.of("GUIDED"));
        assertEquals("GUIDED", service.getAiReplyStyle(student.id()));
        verify(jdbc).queryForList(eq("SELECT CASE WHEN tino_enabled=TRUE THEN 'TINO' ELSE ai_reply_style END FROM app_user WHERE id=? AND enabled=TRUE"), eq(String.class), eq(student.id()));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(student.id()))).thenReturn(List.of());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, () -> service.getAiReplyStyle(student.id())).status());
    }

    private void address(Actor actor, String nickname, String name) {
        when(jdbc.queryForList("SELECT nickname,display_name FROM app_user WHERE id=? AND enabled=TRUE",actor.id()))
                .thenReturn(List.of(Map.of("nickname",nickname,"display_name",name)));
    }
    @Test void aiAddressUsesOwnNicknameWithoutAddedSuffixOrAccountSpecificMappings() {
        Actor second=new Actor("student-2",Role.STUDENT);
        address(student,"Test User","张三"); address(second,"秋月先生","李四");
        assertEquals("Test User",service.getAiAddress(student));
        assertEquals("秋月先生",service.getAiAddress(second));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void aiAddressFallsBackToSchoolNameWithSuffixOnlyForStudents() {
        Actor teacher=new Actor("teacher-2",Role.TEACHER),admin=new Actor("admin-2",Role.ADMIN);
        address(student,"  ","张三");address(teacher,"","李老师");address(admin,"","管理员姓名");
        assertEquals("张三同学",service.getAiAddress(student));
        assertEquals("李老师",service.getAiAddress(teacher));
        assertEquals("管理员姓名",service.getAiAddress(admin));
        address(admin,"Akizuki","管理员姓名");
        assertEquals("Akizuki",service.getAiAddress(admin));
    }
    @Test void aiAddressRejectsControlAndOversizedNamesWithoutMutatingProfiles() {
        for(String invalid:List.of("坏\n昵称","坏\u202E昵称","x".repeat(101))) {
            address(student,invalid,"张三");
            assertEquals("张三同学",service.getAiAddress(student));
        }
        address(student,"","\u202E");
        assertEquals("同学",service.getAiAddress(student));
        Actor admin=new Actor("admin-2",Role.ADMIN);address(admin,"","");
        assertEquals("你",service.getAiAddress(admin));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void aiAddressIsUnavailableForMissingOrDisabledAccount() {
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(BusinessException.class,()->service.getAiAddress(student)).status());
    }
}
