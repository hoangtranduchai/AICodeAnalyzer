package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.SourceCodeDetail;

/**
 * Builds safe, consistent prompts and payloads for AI code analysis requests.
 */
public class AnalysisPromptBuilder {
    private static final String JSON_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": [
                "language",
                "algorithms",
                "data_structures",
                "complexity_time",
                "complexity_space",
                "problem_solving_level",
                "code_quality_score",
                "algorithm_score",
                "ds_score",
                "ai_generated_probability",
                "ai_usage_evidence",
                "explanation_vi",
                "warnings",
                "confidence"
              ],
              "properties": {
                "language": {
                  "type": "string",
                  "enum": ["cpp", "java", "python", "c", "csharp", "javascript", "kotlin", "go", "rust", "other", "unknown"]
                },
                "algorithms": {
                  "type": "array",
                  "uniqueItems": true,
                  "items": {
                    "type": "string",
                    "enum": [
                      "sorting",
                      "binary_search",
                      "two_pointers",
                      "sliding_window",
                      "greedy",
                      "dynamic_programming",
                      "dfs",
                      "bfs",
                      "dijkstra",
                      "floyd_warshall",
                      "union_find",
                      "backtracking",
                      "bitmask",
                      "prefix_sum",
                      "hashing",
                      "number_theory",
                      "graph_traversal",
                      "string_matching",
                      "recursion",
                      "brute_force",
                      "other",
                      "unknown"
                    ]
                  }
                },
                "data_structures": {
                  "type": "array",
                  "uniqueItems": true,
                  "items": {
                    "type": "string",
                    "enum": [
                      "array",
                      "vector",
                      "list",
                      "stack",
                      "queue",
                      "deque",
                      "priority_queue",
                      "set",
                      "map",
                      "hash_map",
                      "hash_set",
                      "tree",
                      "graph",
                      "heap",
                      "disjoint_set",
                      "string",
                      "matrix",
                      "other",
                      "unknown"
                    ]
                  }
                },
                "complexity_time": {
                  "type": "string",
                  "enum": ["O(1)", "O(log n)", "O(n)", "O(n log n)", "O(n^2)", "O(n^3)", "O(2^n)", "O(n!)", "O(V + E)", "O(E log V)", "unknown"]
                },
                "complexity_space": {
                  "type": "string",
                  "enum": ["O(1)", "O(log n)", "O(n)", "O(n log n)", "O(n^2)", "O(V + E)", "unknown"]
                },
                "problem_solving_level": {
                  "type": "string",
                  "enum": ["beginner", "intermediate", "advanced"]
                },
                "code_quality_score": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 100
                },
                "algorithm_score": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 100
                },
                "ds_score": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 100
                },
                "ai_generated_probability": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 100
                },
                "ai_usage_evidence": {
                  "type": "array",
                  "items": {
                    "type": "string",
                    "enum": [
                      "overly_generic_variable_names",
                      "excessive_comments",
                      "template_like_code",
                      "unusual_consistency",
                      "unused_code",
                      "mixed_style",
                      "very_polished_explanation_style",
                      "suspicious_structure_for_level",
                      "no_clear_evidence"
                    ]
                  }
                },
                "explanation_vi": {
                  "type": "string",
                  "minLength": 1
                },
                "warnings": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "confidence": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 100
                }
              }
            }
            """;

    public String systemPrompt() {
        return """
                Ban la chuyen gia thuat toan thi dau lap trinh va phan tich source code.

                Nhiem vu:
                - Phan tich cau truc du lieu, thuat toan, do phuc tap, chat luong code.
                - Uoc luong xac suat source code co dau hieu duoc AI ho tro hoac AI sinh ra.
                - Viet giai thich bang tieng Viet trung lap, ro rang.

                Quy tac bat buoc:
                - Chi tra ve JSON hop le dung schema.
                - Khong bia neu source code hoac metadata khong du du lieu.
                - Neu khong chac chan, dung unknown hoac them warning phu hop.
                - Danh gia AI chi la xac suat/dau hieu tham khao, khong ket luan chac chan nguoi dung da dung AI.
                - Neu source code qua ngan, chi la template, hoac thieu ngu canh de bai, hay giam confidence.
                - Tat ca diem so nam trong khoang 0-100.
                """;
    }

    public String userPrompt(SourceCodeDetail sourceCodeDetail) {
        String sourceCode = sourceCodeDetail.codeContent() == null ? "" : sourceCodeDetail.codeContent();
        return """
                Hay phan tich source code sau theo vai tro chuyen gia thuat toan thi dau lap trinh.

                Thong tin submission:
                - Platform: %s
                - Handle: %s
                - Problem: %s
                - Language khai bao: %s
                - Verdict: %s
                - Submitted at: %s

                JSON Schema bat buoc:
                %s

                Source code:
                ```%s
                %s
                ```

                Yeu cau phan tich:
                1. Xac dinh language thuc te neu co the.
                2. Phat hien algorithms va data_structures.
                3. Uoc luong complexity_time va complexity_space.
                4. Danh gia problem_solving_level.
                5. Cham code_quality_score, algorithm_score, ds_score.
                6. Uoc luong ai_generated_probability tu 0-100.
                7. Liet ke ai_usage_evidence neu co, nhung khong ket luan chac chan.
                8. Viet explanation_vi bang tieng Viet.
                9. Neu source code qua ngan hoac thieu ngu canh, them warning va giam confidence.

                Chi tra ve JSON hop le dung schema. Khong tra ve markdown.
                """.formatted(
                safe(sourceCodeDetail.platformName()),
                safe(sourceCodeDetail.handle()),
                safe(sourceCodeDetail.problemDisplay()),
                safe(sourceCodeDetail.language()),
                safe(sourceCodeDetail.verdict()),
                sourceCodeDetail.submittedAt() == null ? "-" : sourceCodeDetail.submittedAt(),
                JSON_SCHEMA,
                safe(sourceCodeDetail.language()),
                sourceCode
        );
    }

    public String jsonSchema() {
        return JSON_SCHEMA;
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value.trim();
    }
}
