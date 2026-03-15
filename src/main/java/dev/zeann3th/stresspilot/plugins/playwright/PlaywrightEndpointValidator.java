package dev.zeann3th.stresspilot.plugins.playwright;

import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.validators.EndpointTypeValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.pf4j.Extension;

@Extension
public class PlaywrightEndpointValidator implements EndpointTypeValidator {
    @Override
    public boolean supports(String endpointType) {
        return "PLAYWRIGHT".equalsIgnoreCase(endpointType);
    }

    @Override
    public boolean isValid(CreateEndpointRequestDTO request, ConstraintValidatorContext context) {
        Object body = request.getBody();

        if (body == null) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("Playwright plugin requires a script in the body")
            .addConstraintViolation();
            return false;
        }

        return true;
    }
}
