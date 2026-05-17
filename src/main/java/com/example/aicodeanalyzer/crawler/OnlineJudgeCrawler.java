package com.example.aicodeanalyzer.crawler;

/**
 * Common contract for platform-specific crawlers such as Codeforces and VJudge.
 */
public interface OnlineJudgeCrawler {
    String platformCode();

    CrawlResult crawl(CrawlRequest request);
}
