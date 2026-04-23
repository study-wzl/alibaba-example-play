package com.example.springalibabaexample.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.example.springalibabaexample.graph.state.EmailClassification;
import com.example.springalibabaexample.graph.util.States;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.springalibabaexample.graph.util.States.*;

/**
 * 邮件处理工作流的所有节点定义
 */
public final class EmailNodes {

    private static final Logger log = LoggerFactory.getLogger(EmailNodes.class);
    private static final ObjectMapper json = new ObjectMapper();

    private EmailNodes() {}

    // ==================== 读取邮件 ====================
    public static NodeAction readEmail() {
        return state -> {
            String content = getString(state, "email_content");
            log.info("读取邮件: {}", content);
            return Map.of("messages", List.of("Processing email: " + content));
        };
    }

    // ==================== 分类意图 ====================
    public static NodeAction classifyIntent(ChatClient.Builder builder) {
        var client = builder.build();
        return state -> {
            String content = getString(state, "email_content");
            String sender = getString(state, "sender_email", "unknown");

            String prompt = """
                    分析这封客户邮件并进行分类。

                    邮件: %s
                    发件人: %s

                    意图: question / bug / billing / feature / complex
                    紧急程度: low / medium / high / critical

                    只返回JSON: {"intent":"...","urgency":"...","topic":"...","summary":"..."}
                    """.formatted(content, sender);

            String response = client.prompt().user(prompt).call().content();
            EmailClassification classification = parseClassification(response);

            log.info("分类结果: {}, 路由: {}", classification, classification.routeFromIntent());

            return Map.of(
                    "classification", classification,
                    "next_node", classification.routeFromIntent()
            );
        };
    }

    // ==================== 文档搜索 ====================
    public static NodeAction searchDocumentation() {
        return state -> {
            var c = get(state, "classification");
            EmailClassification classification = c instanceof EmailClassification ? (EmailClassification) c : EmailClassification.fallback();
            log.info("搜索文档: {}", classification.getTopic());
            return Map.of(
                    "search_results", List.of(
                            "通过 设置 > 安全 > 更改密码 重置密码",
                            "密码必须至少12个字符",
                            "包含大写字母、小写字母、数字和符号"
                    ),
                    "next_node", "draft_response"
            );
        };
    }

    // ==================== Bug 跟踪 ====================
    public static NodeAction bugTracking() {
        return state -> {
            String ticketId = "BUG-" + System.currentTimeMillis() % 100000;
            log.info("创建工单: {}", ticketId);
            return Map.of(
                    "search_results", List.of("已创建Bug票据 " + ticketId),
                    "next_node", "draft_response"
            );
        };
    }

    // ==================== 起草回复 ====================
    public static NodeAction draftResponse(ChatClient.Builder builder) {
        var client = builder.build();
        return state -> {
            String content = getString(state, "email_content");
            var classification = get(state, "classification");
            EmailClassification cls = classification instanceof EmailClassification ? (EmailClassification) classification : EmailClassification.fallback();

            // 构建上下文
            List<String> context = new ArrayList<>();
            List<String> docs = getList(state, "search_results");
            if (!docs.isEmpty()) {
                context.add("相关文档:\n" + docs.stream().map(d -> "- " + d).collect(Collectors.joining("\n")));
            }

            String prompt = """
                    为这封客户邮件起草回复:
                    %s

                    邮件意图: %s | 紧急程度: %s
                    %s

                    指南: 专业有帮助，解决具体问题，用中文回复。
                    """.formatted(content, cls.getIntent(), cls.getUrgency(), String.join("\n", context));

            String response = client.prompt().user(prompt).call().content();
            String next = cls.needsHumanReview() ? "human_review" : "send_reply";

            log.info("起草完成, 需要审核: {}", cls.needsHumanReview());
            return Map.of("draft_response", response, "next_node", next);
        };
    }

    // ==================== 人工审核 ====================
    public static NodeAction humanReview() {
        return state -> {
            var classification = get(state, "classification");
            EmailClassification cls = classification instanceof EmailClassification ? (EmailClassification) classification : EmailClassification.fallback();
            log.info("等待人工审核 - 意图: {}, 紧急: {}", cls.getIntent(), cls.getUrgency());
            return Map.of(
                    "review_data", Map.of(
                            "original_email", getString(state, "email_content"),
                            "draft_response", getString(state, "draft_response"),
                            "urgency", cls.getUrgency(),
                            "intent", cls.getIntent()
                    ),
                    "status", "waiting_for_review",
                    "next_node", "send_reply"
            );
        };
    }

    // ==================== 发送回复 ====================
    public static NodeAction sendReply() {
        return state -> {
            String draft = getString(state, "draft_response");
            log.info("发送回复: {}...", draft.length() > 80 ? draft.substring(0, 80) : draft);
            return Map.of("status", "sent");
        };
    }

    // ==================== JSON 解析 ====================
    private static EmailClassification parseClassification(String jsonStr) {
        try {
            // 提取 JSON 子串（LLM 可能返回多余文本）
            int start = jsonStr.indexOf('{');
            int end = jsonStr.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return json.readValue(jsonStr.substring(start, end + 1), EmailClassification.class);
            }
        } catch (Exception e) {
            log.warn("解析分类JSON失败: {}", e.getMessage());
        }
        return EmailClassification.fallback();
    }
}
