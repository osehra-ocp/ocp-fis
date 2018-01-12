package gov.samhsa.ocp.ocpfis.service;

import gov.samhsa.ocp.ocpfis.service.dto.OrganizationDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.web.OrganizationController;

import java.util.List;
import java.util.Optional;

public interface OrganizationService {
    /**
     * Gets all available organizations in the configured FHIR server
     *
     * @return
     */
   // List<OrganizationDto> getAllOrganizations(Optional<String> name);

    PageDto<OrganizationDto> getAllOrganizations(Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size);

    PageDto<OrganizationDto> searchOrganizations(OrganizationController.SearchType searchType, String searchValue, Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size);

}
