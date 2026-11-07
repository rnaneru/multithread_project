package org.example.vacancyparser.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParseRequest(
        @NotBlank(message = "query не должен быть пустым")
        @Size(max = 100, message = "query не должен быть длиннее 100 символов")
        String query,

        @Min(value = 1, message = "area должен быть положительным")
        @Max(value = 100_000, message = "area слишком большой")
        Integer area,

        @Min(value = 1, message = "pages должен быть >= 1")
        @Max(value = 10, message = "pages должен быть <= 10")
        Integer pages,

        Boolean enrichDetails
) {
}
