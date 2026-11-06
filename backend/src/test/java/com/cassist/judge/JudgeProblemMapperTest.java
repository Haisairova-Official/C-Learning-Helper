package com.cassist.judge;

import com.cassist.judge.core.JudgeProblemMapper;
import com.cassist.problem.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JudgeProblemMapperTest {
    private ProblemSnapshot snapshot(long codeLimit) {
        return new ProblemSnapshot(new StudentProblemView("p1","v1","title","statement","","",List.of(),
                1000,64,codeLimit,BigDecimal.TEN), "PRIVATE_REFERENCE", List.of(
                new PrivateTestCase("t1","secret-input","2",PrivateTestCase.Visibility.HIDDEN,BigDecimal.TEN,PrivateTestCase.ComparisonMode.EXACT)));
    }
    @Test void mapsAuthorizedSnapshotWithoutReferenceAnswer() {
        var mapped=JudgeProblemMapper.fromAuthorized(snapshot(65536),"int main(void){return 0;}");
        assertEquals("v1",mapped.versionId()); assertEquals(67_108_864,mapped.limits().memoryBytes());
        assertEquals(1,mapped.limits().processLimit()); assertEquals(1,mapped.cases().getFirst().index());
        assertFalse(mapped.toString().contains("PRIVATE_REFERENCE")); assertFalse(mapped.toString().contains("secret-input"));
    }
    @Test void enforcesProblemSpecificUtf8CodeLimit() {
        assertThrows(IllegalArgumentException.class,()->JudgeProblemMapper.fromAuthorized(snapshot(4),"中中"));
    }
}
