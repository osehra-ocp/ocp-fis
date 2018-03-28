package gov.samhsa.ocp.ocpfis.service;

import gov.samhsa.ocp.ocpfis.service.dto.ConsentDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;

import java.util.Optional;

public interface ConsentService {

    PageDto<ConsentDto> getConsents(Optional<String> patient, Optional<String> fromActor, Optional<String> status, Optional<Boolean> isGeneralDesignation,Optional<String> toActor, Optional<Integer> pageNumber, Optional<Integer> pageSize);

}
