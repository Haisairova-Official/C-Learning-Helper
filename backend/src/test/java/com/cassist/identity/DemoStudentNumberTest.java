package com.cassist.identity;

import com.cassist.problem.TeachingService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemoStudentNumberTest {
    @Test void seedsExplicitDemoNumbersOnlyWhenUnassignedWithoutUpdatingProfileFields() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),anyString())).thenReturn(1);
        var initializer=new DemoDataInitializer(jdbc,mock(PasswordEncoder.class),mock(TeachingService.class),new org.springframework.mock.env.MockEnvironment());
        initializer.run(null);
        String update="UPDATE app_user SET student_number=? WHERE id=? AND role='STUDENT' AND (student_number IS NULL OR TRIM(student_number)='')";
        verify(jdbc).update(update,"20260001","demo-student");
        verify(jdbc).update(update,"20260002","demo-student2");
        verify(jdbc,never()).update(contains("nickname"),any(Object[].class));
        verify(jdbc,never()).update(contains("avatar_png"),any(Object[].class));
        verify(jdbc,never()).update(contains("display_name"),any(Object[].class));
    }
}
