package com.cassist.identity;

import com.cassist.common.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserManagementTinoTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UserManagementService users=new UserManagementService(jdbc,mock(PasswordEncoder.class),mock(AvatarNormalizer.class));
    private final Actor admin=new Actor("admin",Role.ADMIN);
    private final String studentId="student";
    private UserManagementService.UserView view(boolean unlocked) {
        return new UserManagementService.UserView(studentId,"login","学校姓名",Role.STUDENT,null,"昵称","简介",AiReplyStyle.GUIDED,true,null,unlocked);
    }
    private UserManagementService.Edit input(AiReplyStyle style) {
        return new UserManagementService.Edit("学校姓名",null,"昵称","简介",style);
    }
    @Test void adminCannotSelectPersonaForLockedAccount() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq(studentId))).thenReturn(List.of(view(false)));
        var error=assertThrows(BusinessException.class,()->users.edit(admin,studentId,input(AiReplyStyle.TINO)));
        assertEquals("TINO_LOCKED",error.code());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void adminCanPreserveUnlockedPersonaWhenEditingOtherProfileFields() {
        when(jdbc.query(anyString(),any(RowMapper.class),eq(studentId))).thenReturn(List.of(view(true)));
        users.edit(admin,studentId,input(AiReplyStyle.TINO));
        verify(jdbc).update(contains("tino_enabled=?"),eq("学校姓名"),isNull(),eq("昵称"),eq("简介"),eq("GUIDED"),eq(true),any(LocalDateTime.class),eq(studentId));
    }
    @Test void nonAdminCannotEditAnotherAccountRegardlessOfStyle() {
        assertThrows(BusinessException.class,()->users.edit(new Actor("teacher",Role.TEACHER),studentId,input(AiReplyStyle.TINO)));
        assertThrows(BusinessException.class,()->users.edit(new Actor("student2",Role.STUDENT),studentId,input(AiReplyStyle.TINO)));
        verifyNoInteractions(jdbc);
    }
}
