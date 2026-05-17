package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.model.Submission;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AIDetectionHeuristicsTest {

    @Test
    void evaluateReturnsProbabilityEvidenceAndEthicalWarnings() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 5, 13, 10, 0);
        SourceCodeDetail current = sourceDetail(10L, submittedAt, """
                #include <bits/stdc++.h>
                using namespace std;

                // Step 1 initialize the required containers
                // Step 2 iterate through the sorted values
                // Step 3 update the answer carefully
                // Step 4 return the final computed answer
                int main() {
                    vector<int> sortedValuesContainer;
                    map<int, int> frequencyCounterByValue;
                    priority_queue<int> candidateProcessingQueue;
                    int currentIndexPointer = 0;
                    int accumulatedAnswerValue = 0;
                    sort(sortedValuesContainer.begin(), sortedValuesContainer.end());
                    while (currentIndexPointer <= accumulatedAnswerValue) {
                        int middleCandidateIndex = (currentIndexPointer + accumulatedAnswerValue) / 2;
                        currentIndexPointer = middleCandidateIndex + 1;
                    }
                    return accumulatedAnswerValue;
                }
                """);

        AIDetectionResult result = new AIDetectionHeuristics().evaluate(
                current,
                similarSourceHistory(submittedAt),
                submissionHistory(submittedAt),
                92,
                88,
                84
        );

        assertTrue(result.aiGeneratedProbability() >= 70);
        assertTrue(result.confidence() >= 50);
        assertTrue(result.evidence().contains("comment_pattern_regular_or_template_like"));
        assertTrue(result.evidence().contains("unusually_descriptive_variable_names_for_contest_code"));
        assertTrue(result.evidence().contains("current_code_quality_or_difficulty_may_not_match_submission_history"));
        assertTrue(result.evidence().contains("multiple_solutions_have_unusually_similar_style"));
        assertTrue(result.evidence().contains("many_hard_submissions_in_short_time_window"));
        assertTrue(result.warnings().getFirst().contains("probabilistic"));
    }

    @Test
    void evaluateKeepsLowProbabilityWhenThereIsNoClearEvidence() {
        AIDetectionResult result = new AIDetectionHeuristics().evaluate(
                sourceDetail(10L, LocalDateTime.of(2026, 5, 13, 10, 0), "int main(){int n;cin>>n;cout<<n;}"),
                List.of(),
                List.of(),
                40,
                20,
                20
        );

        assertTrue(result.aiGeneratedProbability() <= 20);
        assertTrue(result.evidence().contains("no_clear_evidence"));
        assertTrue(result.confidence() < 40);
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("limited")));
    }

    private static List<SourceCodeDetail> similarSourceHistory(LocalDateTime submittedAt) {
        List<SourceCodeDetail> history = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            history.add(sourceDetail(100L + i, submittedAt.minusDays(i + 1), """
                    #include <bits/stdc++.h>
                    using namespace std;
                    int main() {
                        vector<int> values;
                        map<int, int> freq;
                        priority_queue<int> pq;
                        sort(values.begin(), values.end());
                        return 0;
                    }
                    """));
        }
        return history;
    }

    private static List<Submission> submissionHistory(LocalDateTime submittedAt) {
        List<Submission> submissions = new ArrayList<>();
        submissions.add(submission(10L, submittedAt, 2200, "OK"));
        for (int i = 0; i < 8; i++) {
            submissions.add(submission(20L + i, submittedAt.minusDays(10 + i), 900 + i * 30, "OK"));
        }
        submissions.add(submission(40L, submittedAt.minusMinutes(90), 1600, "OK"));
        submissions.add(submission(41L, submittedAt.minusMinutes(40), 1700, "OK"));
        submissions.add(submission(42L, submittedAt.plusMinutes(30), 1650, "OK"));
        submissions.add(submission(43L, submittedAt.plusMinutes(80), 1750, "OK"));
        return submissions;
    }

    private static Submission submission(long submissionId, LocalDateTime submittedAt, int rating, String verdict) {
        return new Submission(
                submissionId,
                1L,
                String.valueOf(submissionId),
                "P" + submissionId,
                "Problem " + submissionId,
                "1",
                "GNU C++17",
                verdict,
                submittedAt,
                46,
                1024L,
                rating,
                "graphs,greedy",
                null,
                submittedAt,
                submittedAt
        );
    }

    private static SourceCodeDetail sourceDetail(long submissionId, LocalDateTime submittedAt, String code) {
        return new SourceCodeDetail(
                submissionId,
                submissionId,
                "CODEFORCES",
                "Codeforces",
                "tourist",
                "remote-" + submissionId,
                "P" + submissionId,
                "Problem " + submissionId,
                "GNU C++17",
                "OK",
                submittedAt,
                "CRAWLED",
                null,
                code,
                "hash-" + submissionId,
                code.split("\\R", -1).length,
                code.length(),
                submittedAt.plusMinutes(1),
                null,
                null
        );
    }
}
