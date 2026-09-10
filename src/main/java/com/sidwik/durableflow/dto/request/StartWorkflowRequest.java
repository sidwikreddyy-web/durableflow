package com.sidwik.durableflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartWorkflowRequest(
        @NotBlank @Size(max = 150) String name
) {
}
