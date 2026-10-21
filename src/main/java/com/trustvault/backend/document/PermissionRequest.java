
package com.trustvault.backend.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionRequest(

        @NotBlank(message = "Username is required")
        @Size(max = 100)
        String username,

        @NotBlank(message = "Permission is required")
        @Pattern(
                regexp = "(?i)READ|EDIT",
                message = "Permission must be READ or EDIT"
        )
        String permission
) {
}
