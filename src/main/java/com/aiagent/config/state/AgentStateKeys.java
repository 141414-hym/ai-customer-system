package com.aiagent.config.state;

/**
 * Canonical keys shared by every Graph node.
 * Keeping the contract in one place prevents silent state typos and makes
 * checkpoint migrations explicit.
 */
public final class AgentStateKeys {
    private AgentStateKeys() {}

    public static final String USER_ID = "userId";
    public static final String TENANT_ID = "tenantId";
    public static final String SESSION_ID = "sessionId";
    public static final String THREAD_ID = "threadId";
    public static final String RUN_ID = "runId";
    public static final String CLIENT_MESSAGE_ID = "clientMessageId";
    public static final String SEQUENCE_NO = "sequenceNo";

    public static final String INPUT = "input";
    public static final String MESSAGES = "messages";
    public static final String HISTORY_SUMMARY = "historySummary";
    public static final String RECENT_HISTORY = "recentHistory";
    public static final String CONVERSATION_STATE = "conversationState";

    public static final String ROUTE = "route";
    public static final String NEXT_ACTION = "nextAction";
    public static final String CONFIDENCE = "confidence";
    public static final String DECISION_REASON = "decisionReason";
    public static final String STATUS = "status";
    public static final String ERROR_CODE = "errorCode";

    public static final String TOOL_NAME = "toolName";
    public static final String TOOL_ARGUMENTS = "toolArguments";
    public static final String TOOL_RESULT = "toolResult";
    public static final String TOOL_ATTEMPT = "toolAttempt";
    public static final String EVIDENCE = "evidence";

    public static final String NODE_COUNT = "nodeCount";
    public static final String MODEL_CALL_COUNT = "modelCallCount";
    public static final String CHECKPOINT_VERSION = "checkpointVersion";
    public static final String WORKFLOW_VERSION = "workflowVersion";
    public static final String DEADLINE_AT = "deadlineAt";
}
