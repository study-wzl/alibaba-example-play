package com.example.springalibabaexample.graph.state;

import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 邮件分类结果 + Graph 状态键策略定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailClassification {

    private static final List<String> HIGH_URGENCY = List.of("high", "critical");

    private String intent;
    private String urgency;
    private String topic;
    private String summary;

    public static EmailClassification fallback() {
        return new EmailClassification("question", "medium", "general", "需要处理的客户邮件");
    }

    /** 是否需要人工审核 */
    public boolean needsHumanReview() {
        return "complex".equals(intent) || HIGH_URGENCY.contains(urgency);
    }

    /** 根据意图决定下一个节点 */
    public String routeFromIntent() {
        return switch (intent) {
            case "question", "feature" -> "search_documentation";
            case "bug" -> "bug_tracking";
            default -> "draft_response";
        };
    }

    public static KeyStrategyFactory strategyFactory() {
        return () -> Map.of(
                "email_content", new ReplaceStrategy(),
                "sender_email", new ReplaceStrategy(),
                "email_id", new ReplaceStrategy(),
                "classification", new ReplaceStrategy(),
                "search_results", new ReplaceStrategy(),
                "draft_response", new ReplaceStrategy(),
                "messages", new AppendStrategy(),
                "next_node", new ReplaceStrategy(),
                "status", new ReplaceStrategy(),
                "review_data", new ReplaceStrategy()
        );
    }
}
