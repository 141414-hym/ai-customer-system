CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128),
    thread_id VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    last_message_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conversation_user UNIQUE (conversation_id, user_id),
    CONSTRAINT uk_conversation_thread UNIQUE (thread_id)
);
CREATE INDEX idx_conversation_user_time ON conversation (user_id, updated_at);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    run_id VARCHAR(64),
    client_message_id VARCHAR(128),
    sequence_no BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uk_message_id UNIQUE (message_id),
    CONSTRAINT uk_client_message UNIQUE (conversation_id, client_message_id),
    CONSTRAINT uk_conversation_sequence UNIQUE (conversation_id, sequence_no)
);
CREATE INDEX idx_message_conversation_sequence ON chat_message (conversation_id, sequence_no);

CREATE TABLE IF NOT EXISTS conversation_state (
    conversation_id VARCHAR(64) PRIMARY KEY,
    state_json JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_summary (
    conversation_id VARCHAR(64) PRIMARY KEY,
    summary_json JSON NOT NULL,
    covered_until_sequence BIGINT NOT NULL DEFAULT 0,
    summary_version BIGINT NOT NULL DEFAULT 0,
    model_name VARCHAR(128),
    prompt_version VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    estimated_delivery_time VARCHAR(64),
    product_name VARCHAR(128),
    CONSTRAINT uk_order_no_user UNIQUE (order_no, user_id)
);
CREATE INDEX idx_order_user_no ON customer_order (user_id, order_no);
