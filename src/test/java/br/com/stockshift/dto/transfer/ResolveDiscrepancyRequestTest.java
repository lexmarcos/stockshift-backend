package br.com.stockshift.dto.transfer;

import br.com.stockshift.model.enums.DiscrepancyResolution;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveDiscrepancyRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void shouldAcceptValidRequest() {
        ResolveDiscrepancyRequest request = new ResolveDiscrepancyRequest();
        request.setResolution(DiscrepancyResolution.WRITE_OFF);
        request.setJustification("Damaged during transport");

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectMissingResolution() {
        ResolveDiscrepancyRequest request = new ResolveDiscrepancyRequest();
        request.setJustification("Some reason");

        var violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("resolution");
    }

    @Test
    void shouldRejectMissingJustification() {
        ResolveDiscrepancyRequest request = new ResolveDiscrepancyRequest();
        request.setResolution(DiscrepancyResolution.WRITE_OFF);

        var violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("justification");
    }
}
