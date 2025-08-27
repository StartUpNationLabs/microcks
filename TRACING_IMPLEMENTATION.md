# OpenTelemetry Tracing in RestInvocationProcessor

## Overview

I've added comprehensive OpenTelemetry tracing to the `RestInvocationProcessor` class to provide visibility into the request processing flow in Microcks. This implementation follows OpenTelemetry best practices by using a single span with low-cardinality attributes for span dimensions and high-cardinality data in events.

## What Was Added

### 1. Tracer Initialization
- Added a `Tracer` field initialized with `GlobalOpenTelemetry.getTracer("microcks.rest-invocation-processor")`
- This connects to the OpenTelemetry instance configured via the Java agent

### 2. Single Span: `rest.invocation.process`
The entire `processInvocation` method is wrapped with a single comprehensive span that includes:

**Low-Cardinality Attributes (suitable for indexing/grouping):**
- `service.name`: The mock service name
- `service.version`: The mock service version  
- `operation.name`: The operation being invoked
- `operation.method`: HTTP method (GET, POST, etc.)
- `dispatcher.type`: Type of dispatcher being used
- `response.delay`: Applied delay in milliseconds
- `response.status`: HTTP status code
- `response.found`: Boolean indicating if response was found

**High-Cardinality Data in Events:**
- `request_details` event with:
  - `resource.path`: The request path
  - `client.address`: Client IP address
  - `client.host`: Client hostname
  - `query.string`: Query parameters
- `request_body` event (if body present) with:
  - `body.length`: Length of request body
  - `body.content`: Body content (truncated to 1000 chars if longer)
- `request_headers` event (if headers present) with:
  - `headers.count`: Number of headers
  - `headers.content`: Header content
- `dispatch_criteria_computed` event with:
  - `dispatch.criteria`: The computed dispatch criteria
- `proxying_request` event (if proxying) with:
  - `proxy.url`: Target proxy URL
- `response_found` event (if response found initially) with:
  - `response.name`: Name of the selected response
  - `response.media_type`: Media type of response
- `fallback_response_found` event (if fallback used) with:
  - `response.name`: Name of the fallback response
  - `response.media_type`: Media type of fallback response
- `random_response_found` event (if random response used) with:
  - `response.name`: Name of the random response
  - `response.media_type`: Media type of random response

**Timeline Events (Results Only):**
- `dispatch_criteria_computed`: When dispatch criteria has been computed (with the actual criteria)
- **Dispatcher-Specific Result Events:**
  - `sequence_dispatch_result`: SEQUENCE dispatcher result (with rules, pattern, path, and result)
  - `script_evaluation_result`: SCRIPT dispatcher result (with success/failure, result/error, and type)
  - `uri_params_dispatch_result`: URI_PARAMS dispatcher result (with rules, full URI, and result)
  - `uri_parts_dispatch_result`: URI_PARTS dispatcher result (with rules, pattern, path, and result)
  - `uri_elements_dispatch_result`: URI_ELEMENTS dispatcher result (with rules, pattern, path, URI, and result)
  - `json_evaluation_result`: JSON_BODY dispatcher result (with success/failure, result/error, and type)
  - `query_header_dispatch_result`: QUERY_HEADER dispatcher result (with rules, header count, and result)
  - `unknown_dispatcher_result`: Unknown dispatcher type (with error details)
- `response_found`: When initial response search succeeds (with response details)
- `fallback_response_found`: When fallback response is found (with fallback response details)
- `random_response_found`: When random response is selected (with random response details)
- `proxying_request`: When request is being proxied (with proxy URL)

## Benefits of Single Span Approach

### Simplified Trace Structure
- **Single span** makes it easier to see the complete request flow at a glance
- **Linear timeline** shows the progression through processing stages
- **Reduced complexity** - no need to correlate multiple spans

### Performance Benefits
- **Lower overhead** - only one span creation/management per request
- **Reduced storage** - fewer span objects in the trace
- **Simpler querying** - all data in one place

### Debugging Advantages
- **Complete context** - all information available in one span
- **Clear timeline** - events show the exact processing order
- **Unified view** - no jumping between parent/child spans

## Error Handling
- All exceptions are recorded using `span.recordException(e)`
- Span status is set to `ERROR` with descriptive messages
- Original exceptions are re-thrown to maintain existing behavior

## Viewing Traces

### With Docker Compose (Dev Mode)
The development Docker Compose setup already includes Jaeger:

1. Start the development environment:
   ```bash
   cd install/docker-compose
   docker compose -f docker-compose-devmode.yml up
   ```

2. Access Jaeger UI at: http://localhost:16686

3. Make some REST API calls to Microcks mocks at: http://localhost:8080

4. View traces in Jaeger by:
   - Selecting "microcks" service
   - Searching for traces
   - Clicking on individual traces to see the detailed flow
   - Expanding events to see high-cardinality data
   - Using the timeline view to see event progression

### Trace Structure
Each REST request will generate a single span:
```
rest.invocation.process
├── Events: request_details, request_body, request_headers
├── Events: dispatch_criteria_computed (with computed criteria)
├── Events: [dispatcher-specific]_dispatch_result (with dispatcher details and results)
└── Events: response_found, fallback_response_found, random_response_found, or proxying_request
```
