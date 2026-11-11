# Design

## Context

The change implements a `/model_info` command that displays detailed information about the AI model currently being used by the bot. This includes model identification, provider information, connection method, token limits, and cost information for models where pricing data is available.

The implementation leverages existing infrastructure:
- The LlmCatalog already contains cost information for models from the polza.ai provider
- The bot already has mechanisms to determine the actual model being used (considering fallbacks)
- The CommandDispatcher handles command processing
- The AiService manages AI interactions and model selection

## Goals / Non-Goals

**Goals:**
- Implement the `/model_info` command as specified in the proposal
- Display comprehensive model information including cost data when available
- Show the actual model being used (not just the configured one)
- Maintain backward compatibility with existing functionality
- Integrate cleanly with existing bot command structure

**Non-Goals:**
- Implementation of model selection commands (that's out of scope)
- Modification of model configuration or selection logic
- Addition of new database tables or schema changes
- Implementation of model cost calculations (costs are already stored in the catalog)

## Decisions

### Command Implementation Location
The `/model_info` command will be implemented in the `CommandDispatcher` class, following the same pattern as existing commands like `/bot_info` and `/clear`.

### Information Display Format
The command will return a formatted text message containing:
1. Provider information
2. Model identifier
3. Model display name
4. Connection method (direct or aggregator)
5. Output token limit
6. Current answer limit
7. Reaction model setting
8. Cost information (input/output price per million tokens in rubles) when available

### Cost Data Handling
Cost information will only be displayed for models that have pricing data in the LlmCatalog. Models without cost data will show the other information but omit the cost fields.

### Actual Model Determination
The command will determine the actual model being used by leveraging existing logic in the `LlmClient` and `AiService` components, ensuring it reflects the real model that would be used for responses rather than just the configured setting.

## Risks / Trade-offs

### Risk: Performance Impact
- **Description**: Fetching model information for every command invocation
- **Mitigation**: Minimal impact as the information is already available in memory through the LlmCatalog

### Risk: Cost Data Availability
- **Description**: Not all models have cost information in the catalog
- **Mitigation**: Graceful degradation - command shows all available information, omitting cost fields for models without pricing data

### Risk: Command Overload
- **Description**: Too much information in a single command response
- **Mitigation**: Structured presentation with clear separation of information categories

## Migration Plan

1. Implement the command handler in CommandDispatcher
2. Create the logic to gather model information from the existing infrastructure
3. Test with various model configurations
4. Update documentation and help text
5. Deploy with standard release process

## Open Questions

None at this time.