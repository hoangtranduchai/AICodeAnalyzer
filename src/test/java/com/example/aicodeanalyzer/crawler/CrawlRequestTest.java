package com.example.aicodeanalyzer.crawler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlRequestTest {

    @Test
    void nonPositiveMaxSubmissionsMeansUnlimited() {
        CrawlRequest request = CrawlRequest.publicOnly("tourist", 0);

        assertTrue(request.isUnlimited());
        assertEquals(CrawlRequest.UNLIMITED_SUBMISSIONS, request.maxSubmissions());
    }
}
