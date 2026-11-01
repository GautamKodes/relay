create table users (
    id bigserial primary key,
    email varchar(255) not null unique,
    display_name varchar(80) not null,
    password_hash varchar(100) not null,
    created_at timestamptz not null default now()
);

create table workspaces (
    id bigserial primary key,
    name varchar(80) not null,
    invite_code varchar(16) not null unique,
    owner_id bigint not null references users (id),
    created_at timestamptz not null default now()
);

create table workspace_members (
    id bigserial primary key,
    workspace_id bigint not null references workspaces (id) on delete cascade,
    user_id bigint not null references users (id) on delete cascade,
    role varchar(16) not null,
    joined_at timestamptz not null default now(),
    unique (workspace_id, user_id)
);

create index idx_workspace_members_user on workspace_members (user_id);

create table channels (
    id bigserial primary key,
    workspace_id bigint not null references workspaces (id) on delete cascade,
    name varchar(80) not null,
    type varchar(16) not null,
    dm_key varchar(40),
    created_by bigint not null references users (id),
    created_at timestamptz not null default now(),
    unique (workspace_id, name),
    unique (workspace_id, dm_key)
);

create table channel_members (
    id bigserial primary key,
    channel_id bigint not null references channels (id) on delete cascade,
    user_id bigint not null references users (id) on delete cascade,
    last_read_message_id bigint not null default 0,
    unique (channel_id, user_id)
);

create index idx_channel_members_user on channel_members (user_id);

create table messages (
    id bigserial primary key,
    channel_id bigint not null references channels (id) on delete cascade,
    user_id bigint not null references users (id),
    parent_id bigint references messages (id) on delete cascade,
    content text not null,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted boolean not null default false
);

create index idx_messages_channel_top on messages (channel_id, id desc) where parent_id is null;
create index idx_messages_parent on messages (parent_id);
create index idx_messages_search on messages using gin (to_tsvector('english', content));

create table reactions (
    id bigserial primary key,
    message_id bigint not null references messages (id) on delete cascade,
    user_id bigint not null references users (id) on delete cascade,
    emoji varchar(16) not null,
    unique (message_id, user_id, emoji)
);
