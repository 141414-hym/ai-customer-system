CREATE TABLE IF NOT EXISTS agent_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_json TEXT,
    error_code VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_task_id UNIQUE (task_id)
);
CREATE INDEX idx_agent_task_user_time ON agent_task (user_id, created_at);
