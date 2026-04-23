package com.example.springalibabaexample.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.example.springalibabaexample.graph.node.EmailNodes;
import com.example.springalibabaexample.graph.state.EmailClassification;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * 邮件处理工作流图定义
 *
 * 流程:
 * START -> 读取邮件 -> 分类意图 ─┬-> 文档搜索 -> 起草回复 ─┬-> 发送回复 -> END
 *                               ├-> Bug跟踪  -> 起草回复 ─┤          ↑
 *                               └-> 起草回复 ─────────────┤     [人工审核]
 *                                                          └-> 人工审核 -> ...
 */
@Component
public class EmailAgentGraph {

    private final CompiledGraph compiledGraph;

    public EmailAgentGraph(ChatClient.Builder chatClientBuilder) {
        try {
            var stateFactory = EmailClassification.strategyFactory();

            this.compiledGraph = new StateGraph(stateFactory)
                    // 注册节点
                    .addNode("read_email", node_async(EmailNodes.readEmail()))
                    .addNode("classify_intent", node_async(EmailNodes.classifyIntent(chatClientBuilder)))
                    .addNode("search_documentation", node_async(EmailNodes.searchDocumentation()))
                    .addNode("bug_tracking", node_async(EmailNodes.bugTracking()))
                    .addNode("draft_response", node_async(EmailNodes.draftResponse(chatClientBuilder)))
                    .addNode("human_review", node_async(EmailNodes.humanReview()))
                    .addNode("send_reply", node_async(EmailNodes.sendReply()))
                    // 固定边
                    .addEdge(START, "read_email")
                    .addEdge("read_email", "classify_intent")
                    .addEdge("search_documentation", "draft_response")
                    .addEdge("bug_tracking", "draft_response")
                    .addEdge("send_reply", END)
                    // 条件边: 分类 -> 路由
                    .addConditionalEdges("classify_intent",
                            edge_async(state -> (String) state.value("next_node").orElse("draft_response")),
                            java.util.Map.of(
                                    "search_documentation", "search_documentation",
                                    "bug_tracking", "bug_tracking",
                                    "draft_response", "draft_response"
                            ))
                    // 条件边: 起草 -> 审核或发送
                    .addConditionalEdges("draft_response",
                            edge_async(state -> (String) state.value("next_node").orElse("send_reply")),
                            java.util.Map.of(
                                    "human_review", "human_review",
                                    "send_reply", "send_reply"
                            ))
                    // 条件边: 审核 -> 发送
                    .addConditionalEdges("human_review",
                            edge_async(state -> (String) state.value("next_node").orElse("send_reply")),
                            java.util.Map.of("send_reply", "send_reply"))
                    // 编译: 注册检查点 + 中断点
                    .compile(com.alibaba.cloud.ai.graph.CompileConfig.builder()
                            .saverConfig(com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig.builder()
                                    .register(new com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver())
                                    .build())
                            .interruptBefore("human_review")
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Graph 编译失败", e);
        }
    }

    public CompiledGraph getGraph() {
        return compiledGraph;
    }
}
