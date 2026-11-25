-- T01: 初始化 7 张表（严格按 docs/02b-contract.md §2 逐列）
-- 不使用任何 Postgres 扩展；主键 UUID 由应用生成；时间列 timestamptz

-- 2.1 alerts_active（活动告警，聚合体）
create table alerts_active (
    id uuid not null,
    fingerprint char(64) not null,
    source varchar(128) not null,
    type varchar(128) not null,
    resource_id varchar(128) not null,
    event_code varchar(128) not null default '',
    severity varchar(16) not null default 'MEDIUM',
    severity_rank smallint not null generated always as (
        case severity
            when 'CRITICAL' then 4
            when 'HIGH' then 3
            when 'MEDIUM' then 2
            when 'LOW' then 1
            when 'INFO' then 0
            else -1
        end
    ) stored,
    message text not null,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    occurrence_count integer not null default 1,
    latest_payload jsonb not null default '{}',
    latest_event_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint pk_alerts_active primary key (id),
    constraint uq_alerts_active_fingerprint unique (fingerprint),
    constraint ck_alerts_active_severity check (severity in ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    constraint ck_alerts_active_message_len check (char_length(message) <= 4000),
    constraint ck_alerts_active_count check (occurrence_count >= 1)
);

create index idx_alerts_active_sort on alerts_active (severity_rank desc, last_seen_at desc, id);
create index idx_alerts_active_source on alerts_active (source, last_seen_at desc);
create index idx_alerts_active_lastseen on alerts_active (last_seen_at desc);

-- 2.2 alerts_history（历史告警）
create table alerts_history (
    id uuid not null,
    fingerprint char(64) not null,
    source varchar(128) not null,
    type varchar(128) not null,
    resource_id varchar(128) not null,
    event_code varchar(128) not null default '',
    severity varchar(16) not null default 'MEDIUM',
    severity_rank smallint not null generated always as (
        case severity
            when 'CRITICAL' then 4
            when 'HIGH' then 3
            when 'MEDIUM' then 2
            when 'LOW' then 1
            when 'INFO' then 0
            else -1
        end
    ) stored,
    message text not null,
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    occurrence_count integer not null default 1,
    latest_payload jsonb not null default '{}',
    latest_event_id uuid not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    cleared_at timestamptz not null,
    cleared_by varchar(128) not null,
    cleared_reason varchar(500),
    clear_mode varchar(16) not null default 'MANUAL',
    constraint pk_alerts_history primary key (id),
    constraint ck_alerts_history_severity check (severity in ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    constraint ck_alerts_history_reason_len check (char_length(cleared_reason) <= 500 or cleared_reason is null),
    constraint ck_alerts_history_mode check (clear_mode in ('MANUAL','SYSTEM')),
    constraint ck_alerts_history_count check (occurrence_count >= 1)
);

create index idx_alerts_history_cleared on alerts_history (cleared_at desc, id);
create index idx_alerts_history_cleared_by on alerts_history (cleared_by, cleared_at desc);
create index idx_alerts_history_source on alerts_history (source, cleared_at desc);
create index idx_alerts_history_fingerprint on alerts_history (fingerprint);

-- 2.3 alert_event（每次上报原文，证据链）
create table alert_event (
    id uuid not null,
    alert_id uuid not null,
    occurred_at timestamptz not null,
    received_at timestamptz not null default now(),
    severity varchar(16) not null,
    message text not null,
    payload jsonb not null default '{}',
    ingress varchar(8) not null,
    constraint pk_alert_event primary key (id),
    constraint ck_alert_event_ingress check (ingress in ('HTTP','KAFKA'))
);

create index idx_alert_event_alert on alert_event (alert_id, occurred_at desc, id);
create index idx_alert_event_received on alert_event (received_at);

-- 2.4 audit_event（审计事实表）
create table audit_event (
    id uuid not null,
    occurred_at timestamptz not null,
    recorded_at timestamptz not null default now(),
    actor varchar(128) not null,
    actor_type varchar(16) not null,
    action varchar(32) not null,
    target_type varchar(16) not null,
    target_id varchar(128),
    outcome varchar(16) not null,
    source varchar(16) not null,
    source_event_id varchar(128),
    client_net varchar(64),
    trace_id varchar(64),
    detail jsonb not null default '{}',
    constraint pk_audit_event primary key (id),
    constraint ck_audit_event_actor_type check (actor_type in ('USER','SYSTEM','SERVICE')),
    constraint ck_audit_event_action check (action in ('LOGIN','LOGOUT','LOGIN_FAILED','ALERT_CLEARED','ACCESS_DENIED')),
    constraint ck_audit_event_target_type check (target_type in ('ALERT','AUTH','ENDPOINT')),
    constraint ck_audit_event_outcome check (outcome in ('SUCCESS','DENIED','FAILED')),
    constraint ck_audit_event_source check (source in ('APP','KEYCLOAK'))
);

create index idx_audit_event_time on audit_event (occurred_at desc, id);
create index idx_audit_event_actor on audit_event (actor, occurred_at desc);
create index idx_audit_event_action on audit_event (action, occurred_at desc);
create index idx_audit_event_outcome on audit_event (outcome, occurred_at desc);
create unique index uq_audit_event_source_event on audit_event (source_event_id) where source_event_id is not null;

-- 2.5 processed_event（Kafka 消费幂等）
create table processed_event (
    consumer_group varchar(128) not null,
    topic varchar(255) not null,
    partition_no integer not null,
    record_offset bigint not null,
    processed_at timestamptz not null default now(),
    constraint pk_processed_event primary key (consumer_group, topic, partition_no, record_offset)
);

-- 2.6 keycloak_cursor（采集游标）
create table keycloak_cursor (
    id varchar(64) not null,
    last_event_time bigint not null default 0,
    last_event_id varchar(128),
    updated_at timestamptz not null default now(),
    constraint pk_keycloak_cursor primary key (id)
);

-- 2.7 notification（下游通知落点）
create table notification (
    id uuid not null,
    alert_id uuid not null,
    channel varchar(16) not null default 'LOG',
    payload jsonb not null default '{}',
    created_at timestamptz not null default now(),
    constraint pk_notification primary key (id),
    constraint ck_notification_channel check (channel in ('LOG'))
);

create index idx_notification_alert on notification (alert_id);
create index idx_notification_created on notification (created_at desc);

