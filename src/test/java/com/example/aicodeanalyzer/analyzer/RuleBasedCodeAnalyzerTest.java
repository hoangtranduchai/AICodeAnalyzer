package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedCodeAnalyzerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void analyzeDetectsCoreDataStructuresAndAlgorithms() throws Exception {
        SourceCodeDetail detail = detail("""
                #include <bits/stdc++.h>
                using namespace std;

                vector<vector<int>> adj;
                map<int, int> freq;
                set<int> seen;
                queue<int> q;
                stack<int> st;
                priority_queue<int> pq;
                int dp[1005];

                void dfs(int u) {
                    seen.insert(u);
                    for (int v : adj[u]) {
                        if (!seen.count(v)) dfs(v);
                    }
                }

                void bfs(int s) {
                    q.push(s);
                    while (!q.empty()) {
                        int u = q.front();
                        q.pop();
                    }
                }

                int main() {
                    int n;
                    cin >> n;
                    int a[1000];
                    vector<int> values(n);
                    sort(values.begin(), values.end());
                    int l = 0, r = n - 1;
                    while (l <= r) {
                        int mid = (l + r) / 2;
                        if (values[mid] < 10) l = mid + 1;
                        else r = mid - 1;
                    }
                    return 0;
                }
                """);

        AiAnalysisResult result = new RuleBasedCodeAnalyzer().analyze(detail);

        assertEquals("RULE_BASED", result.getAnalyzerType());
        assertEquals("local-regex-heuristic", result.getModelName());
        assertTrue(result.getDataStructures().contains("array"));
        assertTrue(result.getDataStructures().contains("vector"));
        assertTrue(result.getDataStructures().contains("map"));
        assertTrue(result.getDataStructures().contains("set"));
        assertTrue(result.getDataStructures().contains("queue"));
        assertTrue(result.getDataStructures().contains("stack"));
        assertTrue(result.getDataStructures().contains("priority_queue"));
        assertTrue(result.getDataStructures().contains("graph"));
        assertTrue(result.getAlgorithms().contains("sorting"));
        assertTrue(result.getAlgorithms().contains("binary_search"));
        assertTrue(result.getAlgorithms().contains("dfs"));
        assertTrue(result.getAlgorithms().contains("bfs"));
        assertTrue(result.getAlgorithms().contains("dynamic_programming"));
        assertTrue(result.getAlgorithms().contains("graph"));
        assertTrue(result.getAlgorithms().contains("greedy"));
        assertTrue(result.getSummary().contains("Heuristic fallback analysis"));
        assertTrue(result.getSummary().contains("not a conclusion"));

        JsonNode rawResponse = OBJECT_MAPPER.readTree(result.getRawResponse());
        assertEquals("rule_based_regex_heuristic", rawResponse.get("analyzer").asText());
        assertTrue(rawResponse.get("confidence").asInt() <= 62);
        assertTrue(rawResponse.get("warnings").get(0).asText().contains("heuristic fallback"));
    }

    @Test
    void analyzeReducesConfidenceForVeryShortSourceCode() throws Exception {
        AiAnalysisResult result = new RuleBasedCodeAnalyzer().analyze(detail("int main(){return 0;}"));
        JsonNode rawResponse = OBJECT_MAPPER.readTree(result.getRawResponse());

        assertTrue(rawResponse.get("confidence").asInt() < 45);
        assertTrue(result.getSummary().contains("regex/heuristics"));
        assertTrue(result.getRawResponse().contains("Source code is too short"));
    }

    @Test
    void analyzeHandlesEmptySourceCodeWithLowConfidenceWarnings() throws Exception {
        AiAnalysisResult result = new RuleBasedCodeAnalyzer().analyze(detail(""));
        JsonNode rawResponse = OBJECT_MAPPER.readTree(result.getRawResponse());

        assertEquals("-", result.getDataStructures());
        assertEquals("-", result.getAlgorithms());
        assertEquals("0.00", result.getCodeQualityScore().toPlainString());
        assertTrue(rawResponse.get("confidence").asInt() <= 20);
        assertTrue(result.getRawResponse().contains("Source code is empty"));
        assertTrue(result.getSummary().contains("no strong data-structure signal"));
    }

    @Test
    void analyzeRejectsNullSourceCodeDetail() {
        assertThrows(NullPointerException.class, () -> new RuleBasedCodeAnalyzer().analyze(null));
    }

    private static SourceCodeDetail detail(String code) {
        return new SourceCodeDetail(
                1L,
                10L,
                "CODEFORCES",
                "Codeforces",
                "tourist",
                "1703A",
                "YES or YES?",
                "GNU C++17",
                "OK",
                LocalDateTime.of(2026, 5, 13, 10, 30),
                code,
                "hash",
                code.split("\\R", -1).length,
                code.length(),
                LocalDateTime.of(2026, 5, 13, 10, 31),
                null
        );
    }
}
