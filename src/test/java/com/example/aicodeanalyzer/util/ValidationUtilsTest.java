package com.example.aicodeanalyzer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilsTest {

    @Test
    void normalizeHandleTrimsSafeOnlineJudgeHandles() {
        assertEquals("tourist_01", ValidationUtils.normalizeHandle("  tourist_01  "));
        assertEquals("team-alpha.2", ValidationUtils.normalizeHandle("team-alpha.2"));
    }

    @Test
    void normalizeHandleRejectsBlankSqlLikeAndOverlongValues() {
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.normalizeHandle("   "));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.normalizeHandle("abc'; DROP TABLE dbo.platforms;--"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.normalizeHandle("a".repeat(101)));
    }

    @Test
    void normalizePlatformCodeMapsDisplayNamesAndUppercasesCodes() {
        assertEquals("CODEFORCES", ValidationUtils.normalizePlatformCode("Codeforces"));
        assertEquals("VJUDGE", ValidationUtils.normalizePlatformCode("vjudge"));
        assertEquals("CUSTOMOJ", ValidationUtils.normalizePlatformCode(" customoj "));
    }

    @Test
    void requireScoreInRangeRejectsInvalidSkillScores() {
        assertEquals(0, ValidationUtils.requireScoreInRange(0, "overall_score"));
        assertEquals(100, ValidationUtils.requireScoreInRange(100, "overall_score"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireScoreInRange(-1, "overall_score"));
        assertThrows(IllegalArgumentException.class, () -> ValidationUtils.requireScoreInRange(101, "overall_score"));
    }

    @Test
    void isSafeHttpUrlAllowsOnlyHttpAndHttpsWithHost() {
        assertTrue(ValidationUtils.isSafeHttpUrl("https://codeforces.com/contest/1703/submission/1"));
        assertTrue(ValidationUtils.isSafeHttpUrl("http://localhost:8080/test"));
        assertFalse(ValidationUtils.isSafeHttpUrl("javascript:alert(1)"));
        assertFalse(ValidationUtils.isSafeHttpUrl("file:///C:/secret.txt"));
        assertFalse(ValidationUtils.isSafeHttpUrl("https:///missing-host"));
        assertFalse(ValidationUtils.isSafeHttpUrl("   "));
    }
}
