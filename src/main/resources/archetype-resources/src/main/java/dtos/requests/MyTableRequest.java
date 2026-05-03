package ${groupId}.dtos.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


public record MyTableRequest(
        @NotBlank
        @NotNull
        @Size(max = 200)
        @Schema(
                description = "A name",
                example = "Juan"
        )
        String name,

        @Size(max = 200)
        @Schema(
                description = "A surname",
                example = "Sanchez"
        )
        String surname,

        @Size(max = 200)
        @Schema(
                description = "A description",
                example = "Example."
        )
        String description,

        Long myTableParentId
) {
}
