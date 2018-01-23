package gov.samhsa.ocp.ocpfis.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotBlank;

import java.util.Optional;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TelecomDto {

    @NotBlank
    private String system;

    @NotBlank
    private String value;

    private String use;
}
