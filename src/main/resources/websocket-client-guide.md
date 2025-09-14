# WebSocket Client Implementation Guide

## Overview
The WebSocket system now includes robust session management with multi-tab support, heartbeat monitoring, and Redis-based session tracking with automatic cleanup of expired sessions.

## Client Connection Requirements

### 1. Authentication
Include JWT token in either:
- `Authorization: Bearer <token>` header during handshake
- `auth` cookie with JWT token

### 2. Ping/Pong Response Handling
The client MUST respond to ping requests to maintain the connection:

```javascript
// Example JavaScript client implementation
const ws = new WebSocket('ws://localhost:8080/ws/matching');

ws.onmessage = function(event) {
    const message = JSON.parse(event.data);

    switch(message.type) {
        case 'PING':
            // REQUIRED: Respond to ping to keep session alive
            ws.send(JSON.stringify({
                type: 'PONG',
                data: null
            }));
            break;

        case 'CONNECTION_ESTABLISHED':
            console.log('Connected to matching service');
            break;

        case 'MATCH_FOUND':
            console.log('Match found!', message.data);
            break;

        // ... handle other message types
    }
};
```

## Message Types

### Outgoing (Server → Client)
- `CONNECTION_ESTABLISHED` - Sent when WebSocket connection is established
- `PING` - Sent every 30 seconds, client MUST respond with PONG
- `MATCH_FOUND` - Match has been found
- `MATCH_ACCEPTED` - Opponent accepted the match
- `MATCH_ACCEPTED_ALL` - All participants accepted, starting match
- `MATCH_EXPIRED` - Match expired or cancelled
- `PONG` - Response to client PING

### Incoming (Client → Server)
- `PONG` - **REQUIRED** response to server PING
- `PING` - Optional ping to server (server will respond with PONG)

## Session Management Features

### Multi-Tab Support
- Users can have multiple tabs/sessions open simultaneously
- Each tab maintains its own WebSocket connection
- User remains "online" as long as at least one tab is connected
- Online status is removed only when ALL tabs are closed

### Automatic Session Cleanup
- Sessions expire after 5 minutes of inactivity (no heartbeat response)
- Heartbeat requests are sent every 30 seconds
- Expired sessions are automatically cleaned up every minute
- Redis stores session data with TTL for consistency

### Session Expiry Timeline
1. Ping sent every 30 seconds
2. If no pong response received within 5 minutes → session marked expired
3. Cleanup job runs every minute to remove expired sessions
4. User removed from online list when all sessions expire

## Online User Tracking

### Endpoints
- `GET /users/online` - Returns all currently online users
  ```json
  {
    "users": [
      {
        "id": "user123",
        "username": "john_doe",
        "avatarUrl": "https://..."
      }
    ],
    "count": 1
  }
  ```

### Implementation Notes
- Online status is stored in Redis with automatic expiration
- Supports multiple server instances (future-ready)
- Efficient caching layer for user data lookup
- Real-time updates as users connect/disconnect

## Error Handling
- Failed pong responses result in session termination
- Network issues automatically clean up stale sessions
- Graceful degradation if Redis is temporarily unavailable

## Configuration
- Session timeout: 300 seconds (5 minutes)
- Ping interval: 30 seconds
- Cleanup interval: 60 seconds
- Redis key prefix: `websocket:`