# AI API Integration

This document describes the AI API integration feature in the dataservice, which demonstrates how Contrast Security agents detect API usage in customer applications.

## Overview

The AI Service demonstrates API detection for:
- **OpenAI SDK** - Chat completions API at `http://mock-openai:8888`

By default, all API calls are **intercepted by a mock HTTP server**, so no actual API calls are made. This allows the Contrast Security agents to instrument and observe API usage without requiring real API keys or making external requests.

The domain name (`mock-openai`) resolves to `localhost` via Kubernetes `hostAliases` configuration, creating a realistic-looking API URL while keeping everything local.

## How It Works

### Default Behavior (Enabled)

When the dataservice starts:

1. **Mock HTTP server** starts on `localhost:8888`
2. **OpenAI client** is initialized pointing to the mock server
3. Mock endpoint is configured to return realistic responses
4. API calls go to the mock server instead of real endpoints
5. **Contrast agents instrument the calls** and log API usage events

### Architecture

```
dataservice
  ↓
[AiService]
  ├─ Starts mock HTTP server on port 8888
  ├─ Initializes OpenAI client (points to mock)
  └─ Configures mock /chat/completions endpoint

[AiController]
  ├─ GET /api/ai/health - Health check
  └─ GET /api/ai/openai - Demo OpenAI call
```

## Testing the Demo

### Prerequisites

- dataservice is running on `http://localhost:8080`
- Contrast Java agent is attached (optional, but recommended to see instrumentation)

### Endpoints

#### 1. Health Check
```bash
curl http://localhost:8080/api/ai/health
```

Response (service enabled):
```
AI Service is operational
```

#### 2. OpenAI API Call
```bash
# Without custom prompt
curl http://localhost:8080/api/ai/openai

# With custom prompt
curl "http://localhost:8080/api/ai/openai?prompt=What%20is%20AI?"
```

## Configuration

Edit [application.properties](src/main/resources/application.properties):

```properties
# Enable/disable service (default: true)
ai.demo.enabled=true

# Mock server port (default: 8888)
ai.demo.port=8888

# Mock server host (default: localhost - used as the server binding address)
ai.demo.host=localhost

# Domain name for API clients (must be resolvable in Kubernetes via hostAliases)
ai.demo.openai.host=mock-openai          # OpenAI SDK will connect to http://mock-openai:8888
```

### Disabling the Service

To disable the service:

```properties
ai.demo.enabled=false
```

When disabled, the service will skip initialization and endpoints will return error responses.

### Kubernetes Configuration

The Helm chart automatically configures DNS resolution for the mock domain via `hostAliases` in [dataservice.yaml](../../contrast-cargo-cats/templates/dataservice.yaml):

```yaml
hostAliases:
  - ip: "127.0.0.1"
    hostnames:
      - "mock-openai"
```

This ensures the domain resolves to `localhost` inside the Kubernetes pod.

### Disabling Demo Mode

To disable demo mode and use real APIs:

```properties
ai.demo.enabled=false
```

When disabled, the service will skip initialization and return "Demo mode disabled" from endpoints.

## API Calls Observed by Contrast Agents

When the Contrast Security Java agent is attached, it will detect and log:

### OpenAI Calls
- **API Provider**: `openai`
- **Model**: `gpt-4-turbo`
- **Endpoint**: `http://mock-openai:8888/chat/completions`
- **Method**: `ChatCompletionServiceImpl.create()`

Log records will include:
- `ai_usage.model` - The model being used
- `ai_usage.api_provider` - The provider (openai)
- `ai_usage.api_url` - The URL being called
- Stack trace showing the call origin

## Mock Responses

The mock server is configured to return realistic responses for each endpoint:

### OpenAI Chat Completions Response
```json
{
  "id": "chatcmpl-demo-123",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "gpt-4",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "This is a demo response from mock server"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

## Demo Flow

### In Kubernetes (Default)

1. **Deploy with Helm** - Kubernetes automatically configures hostAliases
   ```bash
   helm install cargo-cats contrast-cargo-cats/
   ```

2. **Verify service is running**
   ```bash
   kubectl port-forward svc/dataservice 8080:8080
   curl http://localhost:8080/api/ai/health
   ```

3. **Trigger API endpoint** - The mock domain is already resolved via hostAliases
   ```bash
   curl "http://localhost:8080/api/ai/openai?prompt=Hello"
   ```

### Local Development (Docker/localhost)

If running locally, you need to configure DNS resolution. Add to `/etc/hosts`:

```
127.0.0.1 mock-openai
```

Or update `application.properties` to use localhost:

```properties
ai.demo.openai.host=localhost
```

### With Contrast Agent

1. **Start dataservice with Contrast agent**
   ```bash
   java -javaagent:/path/to/contrast.jar \
        -Dcontrast.config.path=/path/to/contrast.properties \
        -jar target/dataservice-0.0.1-SNAPSHOT.jar
   ```

2. **Verify service is running**
   ```bash
   curl http://localhost:8080/api/ai/health
   ```

3. **Trigger API endpoint** - If running locally, ensure `/etc/hosts` is configured
   ```bash
   curl http://localhost:8080/api/ai/openai
   ```

4. **Observe in Contrast UI**
   - Check TeamServer/SaaS for AI usage observations
   - Look for log records with `event_name: "ai_usage"`
   - See model names and API provider information
   - URLs will show `http://mock-openai:8888`

## Implementation Details

### Service Class: `AiService`

- **Initialization** (`@PostConstruct`): Starts WireMock and initializes clients
- **Mock Setup**: Configures endpoints for OpenAI and Anthropic
- **Methods**:
  - `openai(prompt)` - Calls OpenAI chat completion API
  - `anthropic(prompt)` - Calls Anthropic message API
  - `multiProvider(prompt)` - Calls both APIs in sequence
- **Cleanup** (`@PreDestroy`): Stops WireMock and closes clients

### Controller Class: `AiController`

- **Health Endpoint**: `/api/ai/health`
- **OpenAI Endpoint**: `/api/ai/openai` (GET)
- **Anthropic Endpoint**: `/api/ai/anthropic` (GET)
- **Multi-Provider Endpoint**: `/api/ai/multi-provider` (POST)

## Benefits

✅ **No Real API Calls** - All calls are intercepted by WireMock
✅ **No API Keys Required** - Uses test keys (`sk-test-key`, `sk-ant-test-key`)
✅ **Realistic URLs** - Shows `http://mock-openai:8888/v1/...` and `http://mock-anthropic:8888/v1/...`
✅ **Agent Instrumentation** - Contrast agents fully instrument the libraries
✅ **Easy to Control** - Single property to enable/disable
✅ **Fast Responses** - Mock responses are instant
✅ **Container DNS** - Kubernetes hostAliases automatically resolve mock domains to localhost

## Troubleshooting

### Port Already in Use

If port 8888 is already in use:

```properties
ai.demo.port=8889
```

### Service Not Initializing

Check logs for:
```
Initializing AiService. Mock mode enabled: true
WireMock server started on port 8888
OpenAI client initialized with base URL: http://mock-openai:8888
Anthropic client initialized with base URL: http://mock-anthropic:8888
```

### Contrast Agent Not Detecting Calls

Ensure:
- Contrast agent is properly attached
- `contrast.observe.ai_model_usage=true` is set
- Service is running with `ai.demo.enabled=true`
- Endpoints are being called

## Dependencies

- `com.openai:openai-java:4.14.2` - OpenAI SDK
- `com.anthropic:anthropic-java:0.3.5` - Anthropic SDK
- `com.github.tomakehurst:wiremock-jre8:3.0.1` - WireMock for mocking

