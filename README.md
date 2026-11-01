# Relay

A Slack-style team messaging backend built with Spring Boot 3 and Java 21, with a black-and-white web client served from the same app.

## Features

- Workspaces with invite codes, public and private channels, direct messages
- Real-time messaging over WebSocket (STOMP) with typing indicators
- Threads, reactions, message edit and delete
- Presence backed by Redis keys with a TTL
- Unread counts per channel and cursor-based message history
- Full-text search with a PostgreSQL GIN index
- JWT authentication, enforced on REST calls and on WebSocket connect and subscribe

## Architecture

Every message event is published to a Redis pub/sub channel. Each app instance subscribes to it and relays events to its own connected WebSocket clients, so users on different instances see the same conversation in real time. Events are published only after the database transaction commits.

## Run everything

```
docker compose up --build
```

Open http://localhost:8080. Traffic goes through nginx to two app instances.

## Run for development

```
docker compose up -d postgres redis
mvn spring-boot:run
```

Open http://localhost:8080 and create two accounts in separate browser profiles to see live updates.

## Configuration

| Variable | Default |
| --- | --- |
| DB_URL | jdbc:postgresql://localhost:5432/relay |
| DB_USER / DB_PASSWORD | relay / relay |
| REDIS_HOST / REDIS_PORT | localhost / 6379 |
| JWT_SECRET | development value, at least 32 bytes |

## REST API

| Method | Path |
| --- | --- |
| POST | /api/auth/register, /api/auth/login |
| GET | /api/me |
| GET, POST | /api/workspaces |
| POST | /api/workspaces/join |
| GET | /api/workspaces/{id}/people, /channels, /presence, /search?q= |
| POST | /api/workspaces/{id}/channels, /dms |
| GET, POST | /api/channels/{id}/messages |
| POST | /api/channels/{id}/read, /members |
| GET | /api/messages/{id}/thread |
| PATCH, DELETE | /api/messages/{id} |
| POST | /api/messages/{id}/reactions |
| POST | /api/presence/heartbeat |

WebSocket endpoint: `/ws`. Send `Authorization: Bearer <token>` in the STOMP CONNECT headers, subscribe to `/topic/channel.{id}`, and publish to `/app/channels/{id}/messages` or `/app/channels/{id}/typing`.

## Resume bullets

- Built a Slack-style messaging backend with Spring Boot, WebSocket/STOMP, PostgreSQL and Redis, scaling real-time delivery across multiple instances with Redis pub/sub.
- Secured REST and WebSocket traffic with JWT, including per-channel subscription authorization.
- Implemented threads, reactions, presence, unread counts, cursor pagination and full-text search with Flyway-managed schema and Dockerized deployment behind nginx.

## Next steps

Run a load test (k6 or Gatling) against the WebSocket endpoint and add the measured connection count to your resume.
