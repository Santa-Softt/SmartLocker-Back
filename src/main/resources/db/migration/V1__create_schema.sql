CREATE TABLE users (
    id uuid DEFAULT uuidv7() NOT NULL,
    full_name varchar(100) NOT NULL,
    email varchar(100) NOT NULL,
    avatar_url varchar(255),
    has_seen_welcome boolean NOT NULL,
    suspended boolean NOT NULL,
    suspension_time timestamp(6) with time zone,
    role varchar(32) NOT NULL,
    version bigint,
    receive_receipts boolean NOT NULL,
    receives_promotions boolean NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_email ON users (email);

CREATE TABLE refresh_tokens (
    id uuid DEFAULT uuidv7() NOT NULL,
    token_hash varchar(256) NOT NULL,
    revoked boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_used_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_token_user ON refresh_tokens (user_id);

CREATE TABLE lockers (
    id uuid DEFAULT uuidv7() NOT NULL,
    label varchar(255) NOT NULL,
    size varchar(16) NOT NULL,
    state varchar(32) NOT NULL,
    CONSTRAINT pk_lockers PRIMARY KEY (id),
    CONSTRAINT uk_lockers_label UNIQUE (label)
);

CREATE INDEX idx_locker_allocation ON lockers (size, state);

CREATE TABLE business_configs (
    id uuid DEFAULT uuidv7() NOT NULL,
    hold_duration_seconds integer NOT NULL,
    min_rental_duration_minutes integer NOT NULL,
    max_rental_duration_minutes integer NOT NULL,
    penalty_percentage integer NOT NULL,
    streak_threshold integer NOT NULL,
    streak_discount_percentage integer NOT NULL,
    service_status varchar(32),
    CONSTRAINT pk_business_configs PRIMARY KEY (id)
);

CREATE TABLE business_configs_rates (
    business_config_id uuid NOT NULL,
    size varchar(16) NOT NULL,
    hourly_rate numeric(38, 2) NOT NULL,
    CONSTRAINT fk_business_configs_rates_config
        FOREIGN KEY (business_config_id) REFERENCES business_configs (id)
);

CREATE TABLE rentals (
    id uuid DEFAULT uuidv7() NOT NULL,
    state varchar(32),
    start_time timestamp(6) with time zone,
    estimated_end_time timestamp(6) with time zone,
    final_cost numeric(38, 2),
    is_penalized boolean NOT NULL,
    user_id uuid,
    locker_id uuid,
    CONSTRAINT pk_rentals PRIMARY KEY (id),
    CONSTRAINT fk_rentals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_rentals_locker FOREIGN KEY (locker_id) REFERENCES lockers (id)
);

CREATE INDEX idx_rental_user_id ON rentals (user_id);
CREATE INDEX idx_rental_locker_id ON rentals (locker_id);
CREATE INDEX idx_rental_state ON rentals (state);
CREATE INDEX idx_rental_user_id_state ON rentals (user_id, state);
CREATE INDEX idx_rental_state_end_time ON rentals (state, estimated_end_time);
CREATE INDEX idx_rental_state_start_time ON rentals (state, start_time);

CREATE TABLE support_tickets (
    id uuid DEFAULT uuidv7() NOT NULL,
    user_id uuid NOT NULL,
    subject varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    rental_id uuid,
    status varchar(32) NOT NULL,
    priority varchar(32) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_support_tickets PRIMARY KEY (id),
    CONSTRAINT fk_support_tickets_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_ticket_user_id ON support_tickets (user_id);
CREATE INDEX idx_ticket_status ON support_tickets (status);
CREATE INDEX idx_ticket_created_at ON support_tickets (created_at);

CREATE TABLE system_logs (
    id uuid DEFAULT uuidv7() NOT NULL,
    user_id uuid,
    action varchar(80) NOT NULL,
    resource_type varchar(80) NOT NULL,
    resource_id varchar(80),
    details varchar(1000),
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_system_logs PRIMARY KEY (id),
    CONSTRAINT fk_system_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_system_log_user_id ON system_logs (user_id);
CREATE INDEX idx_system_log_resource ON system_logs (resource_type, resource_id);
CREATE INDEX idx_system_log_created_at ON system_logs (created_at);
