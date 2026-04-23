package com.example.springalibabaexample.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.example.springalibabaexample.graph.EmailAgentGraph;
import com.example.springalibabaexample.graph.state.EmailClassification;
import com.example.springalibabaexample.graph.util.States;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;

@Tag(name = "Graph 邮件处理", description = "Spring AI Alibaba Graph 智能邮件处理工作流")
@RestController
@RequestMapping("/graph/email")
public class EmailAgentController {

    private static final Logger log = LoggerFactory.getLogger(EmailAgentController.class);
    private final CompiledGraph graph;

    public EmailAgentController(EmailAgentGraph agentGraph) {
        this.graph = agentGraph.getGraph();
    }

    @Operation(summary = "提交邮件处理")
    @PostMapping("/process")
    public Map<String, Object> processEmail(@RequestBody Map<String, String> request) {
        String threadId = UUID.randomUUID().toString();
        var config = RunnableConfig.builder().threadId(threadId).build();

        graph.stream(buildState(request), config)
                .doOnNext(n -> log.info("[{}] {}", threadId, n.node()))
                .blockLast();

        return buildResponse(threadId, config);
    }

    @Operation(summary = "查询处理状态")
    @GetMapping("/state/{threadId}")
    public Map<String, Object> getState(@PathVariable String threadId) {
        var config = RunnableConfig.builder().threadId(threadId).build();
        return buildResponse(threadId, config);
    }

    @Operation(summary = "人工审核通过")
    @PostMapping("/approve/{threadId}")
    public Map<String, Object> approve(
            @PathVariable String threadId,
            @RequestBody(required = false) Map<String, Object> approval) throws Exception {

        var config = RunnableConfig.builder().threadId(threadId).build();

        Map<String, Object> update = new HashMap<>();
        update.put("approved", true);
        if (approval != null && approval.containsKey("edited_response")) {
            update.put("draft_response", approval.get("edited_response"));
        }

        var updated = graph.updateState(config, update, null);
        graph.stream(null, updated).blockLast();

        return buildResponse(threadId, updated);
    }

    @Operation(summary = "流式处理 (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> stream(@RequestParam String emailContent,
                                             @RequestParam(defaultValue = "unknown@example.com") String senderEmail) {
        String threadId = UUID.randomUUID().toString();
        var config = RunnableConfig.builder().threadId(threadId).build();

        return graph.stream(buildState(emailContent, senderEmail), config)
                .map(output -> Map.<String, Object>of(
                        "node", output.node(),
                        "thread_id", threadId
                ));
    }

    // ==================== 内部方法 ====================

    private Map<String, Object> buildState(Map<String, String> request) {
        return buildState(
                request.getOrDefault("email_content", ""),
                request.getOrDefault("sender_email", "unknown@example.com")
        );
    }

    private Map<String, Object> buildState(String emailContent, String senderEmail) {
        return Map.of(
                "email_content", emailContent,
                "sender_email", senderEmail,
                "email_id", "email_" + System.currentTimeMillis(),
                "messages", new ArrayList<String>()
        );
    }

    private Map<String, Object> buildResponse(String threadId, RunnableConfig config) {
        StateSnapshot snapshot = graph.getState(config);
        var data = snapshot.state().data();
        String status = States.getString(snapshot.state(), "status", "processing");

        var result = new LinkedHashMap<String, Object>();
        result.put("thread_id", threadId);
        result.put("status", status);
        result.put("classification", data.get("classification"));
        result.put("draft_response", data.get("draft_response"));

        if ("waiting_for_review".equals(status)) {
            result.put("message", "等待人工审核，调用 /approve/" + threadId + " 继续");
        } else if ("sent".equals(status)) {
            result.put("message", "已发送");
        }
        return result;
    }
}
