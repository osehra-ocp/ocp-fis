package gov.samhsa.ocp.ocpfis.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivityDefinitionDto {
    private String logicalId;
    private String version;
    private String name;
    private String title;
    private ValueSetDto status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/YYYY")
    private String date;
    private String publisherReference;
    private String description;

    private PeriodDto effectivePeriod;
    private ValueSetDto topic;
    private List<ValueSetDto> relatedArtifact;
    private ValueSetDto kind;

    private TimingDto timing;
    private ActionParticipantDto participant;
}