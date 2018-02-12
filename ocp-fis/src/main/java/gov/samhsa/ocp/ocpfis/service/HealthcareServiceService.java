package gov.samhsa.ocp.ocpfis.service;

import gov.samhsa.ocp.ocpfis.service.dto.HealthcareServiceDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;

import java.util.List;
import java.util.Optional;

public interface HealthcareServiceService {
    PageDto<HealthcareServiceDto> getAllHealthcareServices(Optional<List<String>> statusList, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> page, Optional<Integer> size);

    PageDto<HealthcareServiceDto> getAllHealthcareServicesByOrganization(String organizationResourceId, Optional<String> assignedToLocationId, Optional<List<String>> statusList, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> page, Optional<Integer> size);

    PageDto<HealthcareServiceDto> getAllHealthcareServicesByLocation(String organizationResourceId, String locationId, Optional<List<String>> statusList, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> page, Optional<Integer> size);
    HealthcareServiceDto getHealthcareService(String healthcareServiceId);


    /**
     * @param organizationId
     * @param healthcareServiceDto
     */
    void createHealthcareService(String organizationId, HealthcareServiceDto healthcareServiceDto);

    /**
     * Adds a given location(s) to a HealthcareService
     *
     * @param healthcareServiceId
     * @param organizationResourceId
     * @param locationIdList
     * @return
     */
    void assignLocationToHealthcareService(String healthcareServiceId, String organizationResourceId, List<String> locationIdList);
}