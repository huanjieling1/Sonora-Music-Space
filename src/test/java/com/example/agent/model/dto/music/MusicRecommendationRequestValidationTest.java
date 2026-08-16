package com.example.agent.model.dto.music;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicRecommendationRequestValidationTest {
    private final jakarta.validation.Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsTwentyPagesOfTenTracks() {
        var request = new MusicRecommendationRequest("专注工作", 20, 10, null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.resolvedPage()).isEqualTo(20);
        assertThat(request.resolvedPageSize()).isEqualTo(10);
    }

    @Test
    void rejectsPageOrPageSizeAboveLimits() {
        var request = new MusicRecommendationRequest("专注工作", 21, 11, null);

        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("page", "pageSize");
    }

    @Test
    void keepsLegacyLimitCompatible() {
        var request = new MusicRecommendationRequest("专注工作", null, null, 8);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.resolvedPage()).isEqualTo(1);
        assertThat(request.resolvedPageSize()).isEqualTo(8);
    }
}
