# AI API Integration

This document describes the AI API integration feature in the dataservice, which demonstrates how Contrast Security agents detect AI SDK usage in customer applications.

## Overview

The AI Service makes real chat completion calls using:
- **OpenAI Java SDK** — pointed at a local [Ollama](https://ollama.com) instance running as a Kubernetes pod

No external API keys are required. Ollama runs inside the cluster, serving an OpenAI-compatible API at `http://ollama:11434/v1`. The Contrast Java agent instruments the OpenAI SDK and observes the calls exactly as it would for calls to `api.openai.com`. The default model is `smollm:135m` (~270 MB), the smallest available Ollama model.

## How It Works

When the dataservice starts, `AiService` initializes an `OpenAIOkHttpClient` with `baseUrl=http://ollama:11434/v1`. Ollama runs as a Kubernetes pod, serving an OpenAI-compatible API pre-loaded with a small language model (`smollm:135m` by default) that returns real responses. The Contrast agent instruments the SDK's internal `WithRawResponseImpl` classes, capturing the model name, API URL, and provider.

### Architecture

```
[Browser / Traffic Generator]
          ↓
[frontgateservice]  →  GET /api/ai/openai?prompt=...
          ↓
[dataservice — AiController]
          ↓
[AiService — OpenAI Java SDK]
          ↓
[Ollama pod — ollama:11434/v1]   (OpenAI-compatible)
          ↓
  qwen2.5:0.5b model (real inference)
```

### Startup Sequence

1. Ollama pod starts and launches `ollama serve`
2. Startup script pulls the configured model into the PVC (cached on subsequent restarts)
3. Ollama readiness probe passes once `/api/tags` responds
4. dataservice initializes the OpenAI client pointing at `http://ollama:11434/v1`
5. Contrast agent instruments the OpenAI SDK at load time

## Prerequisites

- **Docker Desktop memory: 12 GB minimum, 14 GB recommended** (Settings → Resources → Memory). The default 8 GB is not sufficient for the full stack plus Ollama.
- Kubernetes cluster is running and `make deploy` has been executed

## Testing the Demo

### Prerequisites

- dataservice is running on `http://localhost:8080`
- Contrast Java agent is attached (optional, but recommended to see instrumentation)

### Endpoints

#### Health Check
```bash
curl http://cargocats.localhost/api/ai/health
```

#### OpenAI API Call (through ingress)
```bash
curl "http://cargocats.localhost/api/ai/openai?prompt=What%20are%20best%20practices%20for%20shipping%20cats%20safely?"
```

Returns a real response from the local model. First call after pod startup may take 5–20 seconds on CPU.

#### Direct to dataservice (with port-forward)
```bash
kubectl port-forward svc/contrast-cargo-cats-dataservice 8080:8080
curl "http://localhost:8080/api/ai/openai?prompt=Hello"
```

## Configuration

Edit [application.properties](src/main/resources/application.properties):

```properties
# Enable/disable AI service (default: true)
ai.demo.enabled=true

# Ollama base URL (default: http://ollama:11434/v1)
ai.demo.openai.base-url=http://ollama:11434/v1

# Model to use — must be available in the Ollama pod (default: smollm:135m)
ai.demo.model=smollm:135m
```

### Changing the Model

Update `values.yaml` to change the model pulled by the Ollama pod and used by the dataservice:

```yaml
ollama:
  model: llama3.2   # or any model available at https://ollama.com/library
```

### Disabling the Service

To disable the service:

```properties
ai.demo.enabled=false
```

When disabled, the service will skip initialization and endpoints will return error responses.

## What Contrast Agents Observe

When the Contrast Java agent is attached, it instruments the OpenAI SDK and emits `ai_usage` log records for every call:

| Field | Value |
|---|---|
| `ai_usage.api_provider` | `openai` |
| `ai_usage.model` | `smollm:135m` (or whichever model is configured) |
| `ai_usage.api_url` | `http://ollama:11434/v1` |
| `event_name` | `ai_usage` |

These appear in the Contrast UI under **AI Usage** in the Security Observability section, identical to observations from production apps calling `api.openai.com`.

## Demo Flow for SEs

1. **Deploy the stack**
   ```bash
   make deploy
   ```

2. **Wait for Ollama to be ready** (~30s on first run while the model downloads)
   ```bash
   kubectl rollout status deployment/ollama
   ```

3. **Trigger the AI endpoint** (or let the traffic generator do it automatically)
   ```bash
   curl "http://cargocats.localhost/api/ai/openai?prompt=Hello"
   ```

4. **Observe in Contrast UI**
   - Navigate to the application in TeamServer/SaaS
   - Look for **AI Usage** observations
   - You will see `ai_usage.api_url = http://ollama:11434/v1` and the model name

## Implementation Details

### Service Class: `AiService`

- **Initialization** (`@PostConstruct`): Initializes the OpenAI client pointing at Ollama
- **Methods**:
  - `openai(prompt)` — Calls the Ollama-served chat completion API via the OpenAI Java SDK
  - `isEnabled()` — Returns whether the service is active
- **Cleanup** (`@PreDestroy`): Closes the OpenAI client

### Controller Class: `AiController`

- **Health Endpoint**: `GET /api/ai/health`
- **OpenAI Endpoint**: `GET /api/ai/openai?prompt=<text>`

## Benefits

✅ **Real AI Responses** — Ollama runs an actual language model inside the cluster  
✅ **No External API Keys** — Uses `apiKey("ollama")` as a placeholder  
✅ **Realistic URL** — Agent captures `http://ollama:11434/v1` as the provider URL  
✅ **Agent Instrumentation** — Contrast agents fully instrument the OpenAI Java SDK  
✅ **Configurable Model** — Change `ollama.model` in `values.yaml` to use any Ollama-supported model  
✅ **Model Caching** — PVC stores the downloaded model; restarts are instant after first pull  

## Troubleshooting

### Ollama pod stuck in `Pending` or `Init`

Most likely cause: insufficient Docker Desktop memory.

- Go to **Docker Desktop → Settings → Resources → Memory**
- Set to at least **12 GB** and apply
- Run `make deploy` again

Check Ollama logs:
```bash
kubectl logs deployment/ollama
```

### Model pull fails

Check internet connectivity from the pod and verify the model name is valid:
```bash
kubectl exec deployment/ollama -- ollama list
```

Available models: https://ollama.com/library

### dataservice fails to connect to Ollama

The dataservice starts before Ollama finishes pulling its model. Wait for Ollama to be ready, then restart the dataservice:

```bash
kubectl rollout status deployment/ollama
kubectl rollout restart deployment/contrast-cargo-cats-dataservice
```

### Slow responses

Ollama runs on CPU inside Docker Desktop. Expect 2–10 seconds per request for `smollm:135m`. This is expected and still fully exercises the Contrast instrumentation.

### Contrast agent not detecting calls

Ensure:
- Contrast agent is properly attached to the dataservice pod
- `ai.demo.enabled=true` in application.properties
- The Ollama pod is `Running` and passing its readiness probe
- The endpoint is being reached (check dataservice logs for `Calling OpenAI API`)

## Dependencies

- `com.openai:openai-java:4.14.2` - OpenAI SDK
- `com.anthropic:anthropic-java:0.3.5` - Anthropic SDK
- `com.github.tomakehurst:wiremock-jre8:3.0.1` - WireMock for mocking

