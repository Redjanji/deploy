CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    property_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    agent_id BIGINT,
    appointment_time TIMESTAMP NOT NULL,
    status TINYINT DEFAULT 0,
    remark VARCHAR(500),
    cancel_reason VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_property ON bookings(property_id);
CREATE INDEX IF NOT EXISTS idx_agent ON bookings(agent_id);
CREATE INDEX IF NOT EXISTS idx_status_time ON bookings(status, appointment_time);
