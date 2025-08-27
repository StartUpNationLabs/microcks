# Real-time Span Streaming with Server-Sent Events

## Overview

The TracingController now provides a real-time streaming endpoint for OpenTelemetry spans using Server-Sent Events (SSE) and Spring's event system. This allows clients to receive new span data in real-time as it becomes available, without the need for polling.

## Endpoint

```
GET /api/traces/operations/spans/stream?serviceName={service}&operationName={operation}
```

### Parameters

- `serviceName` (required): The name of the service to filter spans by
- `operationName` (required): The name of the operation to filter spans by

### Response

The endpoint returns a `text/event-stream` that sends JSON data for new spans matching the specified service and operation criteria.

## Architecture

### Event-Driven Approach

The implementation uses Spring's event system instead of polling:

1. **SpanStoredEvent**: Published whenever a new span is stored in `SpanStorageService`
2. **Event Listener**: `TracingController.handleSpanStoredEvent()` listens for these events
3. **Real-time Filtering**: Events are filtered and sent to relevant SSE subscribers immediately

### Components

#### SpanStoredEvent
- Custom Spring ApplicationEvent containing the stored span and trace ID
- Published by `SpanStorageService.storeSpan()`

#### TracingController
- Manages SSE subscriptions via an internal `SseSubscription` class
- Listens for `SpanStoredEvent` and forwards matching spans to subscribers
- Handles client lifecycle (connection, timeout, errors)

#### SpanStorageService
- Modified to publish `SpanStoredEvent` when storing spans
- Uses `ApplicationEventPublisher` for event publishing

## Usage Example

### JavaScript Client

```javascript
const eventSource = new EventSource('/api/traces/operations/spans/stream?serviceName=my-service&operationName=my-operation');

eventSource.addEventListener('span', function(event) {
    const spanData = JSON.parse(event.data);
    console.log('New span received:', spanData);
});

eventSource.addEventListener('error', function(event) {
    console.error('SSE error:', event);
});

// Close the connection when done
// eventSource.close();
```

### curl Example

```bash
curl -N -H "Accept: text/event-stream" \
  "http://localhost:8080/api/traces/operations/spans/stream?serviceName=my-service&operationName=my-operation"
```

## Benefits

1. **Real-time Updates**: No polling delay - spans are sent immediately when available
2. **Efficient**: Event-driven approach reduces CPU usage compared to polling
3. **Scalable**: Spring's async event handling supports multiple concurrent subscribers
4. **Resource Management**: Automatic cleanup of subscriptions on client disconnect

## Configuration

The SSE connections have a 30-minute timeout by default. This can be adjusted in the controller if needed.

## Error Handling

- Client disconnections are automatically detected and cleaned up
- SSE errors complete the stream and remove the subscription
- Timeouts automatically clean up resources

## Comparison with Original Endpoint

| Aspect | Original `/operations/spans` | New `/operations/spans/stream` |
|--------|------------------------------|--------------------------------|
| Method | Request/Response | Server-Sent Events |
| Data | All historical spans | New spans only (real-time) |
| Updates | Manual refresh required | Automatic push |
| Resource Usage | High (repeated queries) | Low (event-driven) |
| Use Case | One-time data retrieval | Real-time monitoring |
