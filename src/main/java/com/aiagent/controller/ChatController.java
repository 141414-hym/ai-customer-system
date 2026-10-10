package com.aiagent.controller;

import com.aiagent.dto.ChatRequest;
import com.aiagent.dto.ChatEvent;
import com.aiagent.session.SessionKey;
import com.aiagent.session.DistributedSessionLock;
import com.aiagent.session.GlobalExecutionGate;
import com.aiagent.security.CurrentUserProvider;
import com.aiagent.persistence.ConversationMessageRepository;
import com.aiagent.memory.RedisContextStore;
import com.aiagent.memory.SummaryTrigger;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aiagent.observability.TraceIdFilter;
import com.aiagent.guard.RunLimits;
import com.aiagent.task.ChatTaskService;
import com.aiagent.observability.ChatMetrics;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final CompiledGraph customerSupportGraph;
    private final CurrentUserProvider currentUserProvider;
    private final ConversationMessageRepository messageRepository;
    private final RedisContextStore contextStore;
    private final DistributedSessionLock distributedSessionLock;
    private final GlobalExecutionGate globalExecutionGate;
    private final SummaryTrigger summaryTrigger;
    private final ChatTaskService chatTaskService;
    private final ChatMetrics chatMetrics;
    private final RunLimits runLimits = RunLimits.defaults();

    public ChatController(CompiledGraph customerSupportGraph,
                          CurrentUserProvider currentUserProvider,
                          ConversationMessageRepository messageRepository,
                          RedisContextStore contextStore,
                          DistributedSessionLock distributedSessionLock,
                          GlobalExecutionGate globalExecutionGate,
                          SummaryTrigger summaryTrigger, ChatTaskService chatTaskService,
                          ChatMetrics chatMetrics) {
        this.customerSupportGraph = customerSupportGraph;
        this.currentUserProvider = currentUserProvider;
        this.messageRepository = messageRepository;
        this.contextStore = contextStore;
        this.distributedSessionLock = distributedSessionLock;
        this.globalExecutionGate = globalExecutionGate;
        this.summaryTrigger = summaryTrigger;
        this.chatTaskService = chatTaskService;
        this.chatMetrics = chatMetrics;
    }

    @PostMapping(value = "/ask", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ask(
          @Valid @RequestBody ChatRequest request
  ) {
      SessionKey sessionKey = currentUserProvider.sessionKey(request.sessionId());
      runLimits.checkInput(request.message());

      List<ChatEvent> events = executeGraph(request, sessionKey)
              .collectList()
              .block();

      return Map.of(
              "userId", sessionKey.userId(),
              "sessionId", sessionKey.sessionId(),
              "events", events == null ? List.of() : events
      );
  }


   @PostMapping(
          value = "/ask/stream",
          produces = MediaType.TEXT_EVENT_STREAM_VALUE
  )
  public Flux<ChatEvent> askStream(
          @Valid @RequestBody ChatRequest request
  ) {
      SessionKey sessionKey = currentUserProvider.sessionKey(request.sessionId());
      runLimits.checkInput(request.message());

      return executeGraph(request, sessionKey);
  }

  @PostMapping(value = "/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> submitTask(@Valid @RequestBody ChatRequest request) {
      SessionKey sessionKey = currentUserProvider.sessionKey(request.sessionId());
      runLimits.checkInput(request.message());
      ChatTaskService.TaskRecord task = chatTaskService.submit(sessionKey.userId(), sessionKey.sessionId(),
              () -> executeGraph(request, sessionKey).collectList().block());
      return Map.of("taskId", task.taskId(), "status", task.status());
  }

  @org.springframework.web.bind.annotation.GetMapping(value = "/tasks/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ChatTaskService.TaskRecord getTask(@org.springframework.web.bind.annotation.PathVariable String taskId) {
      return chatTaskService.get(taskId, currentUserProvider.userId());
  }


    private Flux<ChatEvent> executeGraph(ChatRequest request, SessionKey sessionKey) {
        return Flux.defer(() -> {
            DistributedSessionLock.Lease lease = distributedSessionLock.tryAcquire(sessionKey);
            if (lease == null) {
                return Flux.error(new IllegalStateException("当前会话正在处理中，请稍后重试"));
            }
            String runId = UUID.randomUUID().toString();
            try {
                messageRepository.ensureConversation(sessionKey.sessionId(), sessionKey.userId(),
                        currentUserProvider.tenantId(), sessionKey.threadId());
                var persisted = messageRepository.appendUserMessage(sessionKey.sessionId(), request.clientMessageId(),
                        runId, request.message().trim());
                if (!persisted.newlyCreated()) {
                    return messageRepository.findCompletedAnswer(sessionKey.sessionId(), request.clientMessageId())
                            .map(answer -> Flux.just(idempotentEvent(sessionKey, answer)))
                            .orElseGet(() -> Flux.error(new IllegalStateException("重复请求仍在处理中，请稍后重试")));
                }
                contextStore.appendMessage(sessionKey.userId(), sessionKey.sessionId(),
                        persisted.sequenceNo(), persisted.role(), persisted.content());
            } catch (RuntimeException ex) {
                distributedSessionLock.release(lease);
                return Flux.error(ex);
            }
            Map<String, Object> input = toGraphInput(request, sessionKey);
            long graphStartNanos = System.nanoTime();
            AtomicLong previousNodeNanos = new AtomicLong(graphStartNanos);
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionKey.threadId())
                    .build();
            return globalExecutionGate.execute(() -> {
            AtomicReference<String> answer = new AtomicReference<>("");
            return customerSupportGraph.stream(input, config)
                    .map(event -> toEvent(event, sessionKey, previousNodeNanos))
                    .take(runLimits.maxGraphNodes())
                    .doOnNext(event -> {
                        Object value = event.state().get("answer");
                        if (value != null && !String.valueOf(value).isBlank()) {
                            answer.set(String.valueOf(value));
                        }
                    })
                    .timeout(Duration.ofSeconds(30))
                    .doOnComplete(() -> {
                        persistAssistantAnswer(sessionKey, runId, answer.get(), "COMPLETED");
                        chatMetrics.completed("graph", System.nanoTime() - graphStartNanos);
                    })
                    .doOnError(error -> {
                        log.error("[chat_timing] traceId={} stage=graph_failed error={}",
                                MDC.get(TraceIdFilter.TRACE_ID), error.toString());
                        persistAssistantAnswer(sessionKey, runId, "", "FAILED");
                        chatMetrics.failed("graph");
                    });
            }).doFinally(signal -> distributedSessionLock.release(lease));
        });
    }

    private ChatEvent idempotentEvent(SessionKey sessionKey, String answer) {
        return new ChatEvent("completed", "idempotent_replay", sessionKey.userId(), sessionKey.sessionId(),
                Map.of("answer", answer, "idempotentReplay", true));
    }

    private void persistAssistantAnswer(SessionKey sessionKey, String runId, String answer, String status) {
        if ("COMPLETED".equals(status) && answer.isBlank()) return;
        var saved = messageRepository.appendAssistantMessage(sessionKey.sessionId(), runId, answer, status);
        if ("COMPLETED".equals(status)) {
            contextStore.appendMessage(sessionKey.userId(), sessionKey.sessionId(),
                    saved.sequenceNo(), saved.role(), saved.content());
            summaryTrigger.maybeCompress(sessionKey.userId(), sessionKey.sessionId());
        }
    }

    private ChatEvent toEvent(NodeOutput event, SessionKey sessionKey, AtomicLong previousNodeNanos) {
        Map<String, Object> publicState = new LinkedHashMap<>();
        copyIfPresent(event, publicState, "route");
        copyIfPresent(event, publicState, "confidence");
        copyIfPresent(event, publicState, "decisionReason");
        copyIfPresent(event, publicState, "status");
        copyIfPresent(event, publicState, "answer");
        copyIfPresent(event, publicState, "evidenceSufficient");
        copyIfPresent(event, publicState, "evidenceCount");
        copyIfPresent(event, publicState, "evidenceReason");
        copyIfPresent(event, publicState, "citations");
        copyIfPresent(event, publicState, "answerGuard");
        copyIfPresent(event, publicState, "traceId");
        copyIfPresent(event, publicState, "promptBuildMs");
        copyIfPresent(event, publicState, "modelCallMs");
        copyIfPresent(event, publicState, "answerGuardMs");
        copyIfPresent(event, publicState, "aiReplyTotalMs");
        copyIfPresent(event, publicState, "modelRole");
        // A graph node is an independent child span of the request span.
        publicState.put("spanId", UUID.randomUUID().toString());
        long now = System.nanoTime();
        long previous = previousNodeNanos.getAndSet(now);
        publicState.put("elapsedMs", Math.max(0L, (now - previous) / 1_000_000L));

        return new ChatEvent(
                "node_completed",
                event.node(),
                sessionKey.userId(),
                sessionKey.sessionId(),
                publicState
        );
    }

    private static void copyIfPresent(NodeOutput event,
                                      Map<String, Object> target,
                                      String key) {
        Object value = event.state().data().get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private static String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
    private Map<String, Object> toGraphInput(
          ChatRequest request,
          SessionKey sessionKey
  ) {
      String message = request.message().trim();

      return Map.of(
              "userId", sessionKey.userId(),
              "sessionId", sessionKey.sessionId(),
              "input", message,
              "tenantId", currentUserProvider.tenantId(),
              "productCode", value(request.productCode(), ""),
              "locale", value(request.locale(), "zh-CN"),
              "traceId", value(MDC.get(TraceIdFilter.TRACE_ID), ""),
              "messages", List.of(
                      Map.of(
                              "role", "user",
                              "content", message
                      )
              )
      );
  }

}
