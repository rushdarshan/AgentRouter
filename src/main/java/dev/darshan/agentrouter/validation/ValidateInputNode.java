package dev.darshan.agentrouter.validation;

import dev.darshan.agentrouter.core.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pipeline Node 2: ValidateInput
 *
 * Pure gate: validate context.toolInput against context.selectedTool.schema.
 * No mutation — pass or fail. On failure, populates context.error.
 */
@Component
public class ValidateInputNode {

    private static final Logger log = LoggerFactory.getLogger(ValidateInputNode.class);

    private final SchemaValidator validator;

    public ValidateInputNode(SchemaValidator validator) {
        this.validator = validator;
    }

    /**
     * Validate tool input against schema.
     * On validation failure, populates context error and returns.
     */
    public ExecutionContext execute(ExecutionContext context) {
        log.info("ValidateInput: validating input for tool '{}'",
                context.getSelectedTool().getName());

        try {
            validator.validate(context.getToolInput(), context.getSelectedTool().getSchema());
            log.info("ValidateInput: validation passed");
            return context;

        } catch (ValidationException e) {
            log.warn("ValidateInput: validation failed — {}", e.getMessage());
            return context.withError(e);
        }
    }
}
