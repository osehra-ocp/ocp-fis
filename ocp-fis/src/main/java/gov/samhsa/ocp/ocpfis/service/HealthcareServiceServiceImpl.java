package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.validation.FhirValidator;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.domain.SearchKeyEnum;
import gov.samhsa.ocp.ocpfis.service.dto.HealthcareServiceDto;
import gov.samhsa.ocp.ocpfis.service.dto.NameLogicalIdIdentifiersDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.service.exception.BadRequestException;
import gov.samhsa.ocp.ocpfis.service.exception.DuplicateResourceFoundException;
import gov.samhsa.ocp.ocpfis.service.exception.ResourceNotFoundException;
import gov.samhsa.ocp.ocpfis.util.FhirUtil;
import gov.samhsa.ocp.ocpfis.util.PaginationUtil;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.HealthcareService;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HealthcareServiceServiceImpl implements HealthcareServiceService {

    private final ModelMapper modelMapper;
    private final IGenericClient fhirClient;
    private final FhirValidator fhirValidator;
    private final FisProperties fisProperties;

    @Autowired
    public HealthcareServiceServiceImpl(ModelMapper modelMapper, IGenericClient fhirClient, FhirValidator fhirValidator, FisProperties fisProperties) {
        this.modelMapper = modelMapper;
        this.fhirClient = fhirClient;
        this.fhirValidator = fhirValidator;
        this.fisProperties = fisProperties;
    }

    @Override
    public PageDto<HealthcareServiceDto> getAllHealthcareServices(Optional<List<String>> statusList, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> pageNumber, Optional<Integer> pageSize) {
        int numberOfHealthcareServicesPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.HealthcareService.name());

        Bundle firstPageHealthcareServiceSearchBundle;
        Bundle otherPageHealthcareServiceSearchBundle;
        boolean firstPage = true;
        Map<String, String> locationNameMap = new HashMap<>();

        IQuery healthcareServicesSearchQuery = fhirClient.search().forResource(HealthcareService.class);

        //Check for healthcare service status
        if (statusList.isPresent() && statusList.get().size() == 1) {
            log.info("Searching for healthcare service with the following specific status" + statusList.get().get(0));
            statusList.get().forEach(log::info);
            if (statusList.get().get(0).trim().equalsIgnoreCase("active")
                    || statusList.get().get(0).trim().equalsIgnoreCase("true")) {
                healthcareServicesSearchQuery.where(new TokenClientParam("active").exactly().codes("true"));
            } else if (statusList.get().get(0).trim().equalsIgnoreCase("inactive")
                    || statusList.get().get(0).trim().equalsIgnoreCase("false")) {
                healthcareServicesSearchQuery.where(new TokenClientParam("active").exactly().codes("false"));
            } else {
                log.info("Searching for healthcare services with ALL statuses");
            }
        } else {
            log.info("Searching for healthcare services with ALL statuses");
        }

        // Check if there are any additional search criteria
        healthcareServicesSearchQuery = addAdditionalSearchConditions(healthcareServicesSearchQuery, searchKey, searchValue);

        //The following bundle only contains Page 1 of the resultSet
        firstPageHealthcareServiceSearchBundle = PaginationUtil.getSearchBundleFirstPage(healthcareServicesSearchQuery, numberOfHealthcareServicesPerPage, Optional.empty());

        if (firstPageHealthcareServiceSearchBundle == null || firstPageHealthcareServiceSearchBundle.getEntry().isEmpty()) {
            log.info("No Healthcare Services were found for the given criteria.");
            return new PageDto<>(new ArrayList<>(), numberOfHealthcareServicesPerPage, 0, 0, 0, 0);
        }

        log.info("FHIR Healthcare Service(s) bundle retrieved " + firstPageHealthcareServiceSearchBundle.getTotal() + " Healthcare Service(s) from FHIR server successfully");
        otherPageHealthcareServiceSearchBundle = firstPageHealthcareServiceSearchBundle;
        if (pageNumber.isPresent() && pageNumber.get() > 1) {
            // Load the required page
            firstPage = false;
            otherPageHealthcareServiceSearchBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, firstPageHealthcareServiceSearchBundle, pageNumber.get(), numberOfHealthcareServicesPerPage);
        }
        List<Bundle.BundleEntryComponent> retrievedHealthcareServices = otherPageHealthcareServiceSearchBundle.getEntry();

        //Arrange Page related info
        List<HealthcareServiceDto> healthcareServicesList = retrievedHealthcareServices.stream().map(hcs -> convertHealthcareServiceBundleEntryToHealthcareServiceDto(hcs, locationNameMap, Optional.empty())).collect(Collectors.toList());
        double totalPages = Math.ceil((double) otherPageHealthcareServiceSearchBundle.getTotal() / numberOfHealthcareServicesPerPage);
        int currentPage = firstPage ? 1 : pageNumber.get();

        return new PageDto<>(healthcareServicesList, numberOfHealthcareServicesPerPage, totalPages, currentPage, healthcareServicesList.size(), otherPageHealthcareServiceSearchBundle.getTotal());
    }

    @Override
    public PageDto<HealthcareServiceDto> getAllHealthcareServicesByOrganization(String organizationResourceId, Optional<String> assignedToLocationId, Optional<List<String>> statusList, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> pageNumber, Optional<Integer> pageSize) {

        int numberOfHealthcareServicesPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.HealthcareService.name());

        Bundle firstPageHealthcareServiceSearchBundle;
        Bundle otherPageHealthcareServiceSearchBundle;
        boolean firstPage = true;
        Map<String, String> locationNameMap = new HashMap<>();

        IQuery healthcareServicesSearchQuery = fhirClient.search().forResource(HealthcareService.class).where(new ReferenceClientParam("organization").hasId(organizationResourceId));

        //Check for healthcare service status
        if (statusList.isPresent() && statusList.get().size() == 1) {
            log.info("Searching for healthcare service with the following specific status" + statusList.get().get(0) + " for the given OrganizationID:" + organizationResourceId);
            statusList.get().forEach(log::info);
            if (statusList.get().get(0).trim().equalsIgnoreCase("active")
                    || statusList.get().get(0).trim().equalsIgnoreCase("true")) {
                healthcareServicesSearchQuery.where(new TokenClientParam("active").exactly().codes("true"));
            } else if (statusList.get().get(0).trim().equalsIgnoreCase("inactive")
                    || statusList.get().get(0).trim().equalsIgnoreCase("false")) {
                healthcareServicesSearchQuery.where(new TokenClientParam("active").exactly().codes("false"));
            } else {
                log.info("Searching for healthcare services with ALL statuses for the given OrganizationID:" + organizationResourceId);
            }
        } else {
            log.info("Searching for healthcare services with ALL statuses for the given OrganizationID:" + organizationResourceId);
        }

        // Check if there are any additional search criteria
        healthcareServicesSearchQuery = addAdditionalSearchConditions(healthcareServicesSearchQuery, searchKey, searchValue);

        //The following bundle only contains Page 1 of the resultSet with location
        firstPageHealthcareServiceSearchBundle = PaginationUtil.getSearchBundleFirstPage(healthcareServicesSearchQuery, numberOfHealthcareServicesPerPage, Optional.empty());

        if (firstPageHealthcareServiceSearchBundle == null || firstPageHealthcareServiceSearchBundle.getEntry().isEmpty()) {
            log.info("No Healthcare Service found for the given OrganizationID:" + organizationResourceId);
            return new PageDto<>(new ArrayList<>(), numberOfHealthcareServicesPerPage, 0, 0, 0, 0);
        }

        log.info("FHIR Healthcare Service(s) bundle retrieved " + firstPageHealthcareServiceSearchBundle.getTotal() + " healthcare service(s) from FHIR server successfully");

        otherPageHealthcareServiceSearchBundle = firstPageHealthcareServiceSearchBundle;
        if (pageNumber.isPresent() && pageNumber.get() > 1) {
            // Load the required page
            firstPage = false;
            otherPageHealthcareServiceSearchBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, otherPageHealthcareServiceSearchBundle, pageNumber.get(), numberOfHealthcareServicesPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedHealthcareServices = otherPageHealthcareServiceSearchBundle.getEntry();

        //Arrange Page related info
        List<HealthcareServiceDto> healthcareServicesList = retrievedHealthcareServices.stream().map(hcs -> convertHealthcareServiceBundleEntryToHealthcareServiceDto(hcs, locationNameMap, assignedToLocationId)).collect(Collectors.toList());
        double totalPages = Math.ceil((double) otherPageHealthcareServiceSearchBundle.getTotal() / numberOfHealthcareServicesPerPage);
        int currentPage = firstPage ? 1 : pageNumber.get();

        return new PageDto<>(healthcareServicesList, numberOfHealthcareServicesPerPage, totalPages, currentPage, healthcareServicesList.size(), otherPageHealthcareServiceSearchBundle.getTotal());
    }

    @Override
    public PageDto<HealthcareServiceDto> getAllHealthcareServicesByLocation(String organizationResourceId, String locationId, Optional<List<String>> statusList, Optional<String> searchKey, Optional<String> searchValue, Optional<Integer> pageNumber, Optional<Integer> pageSize) {
        int numberOfHealthcareServicesPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.HealthcareService.name());

        Bundle firstPageHealthcareServiceSearchBundle;
        Bundle otherPageHealthcareServiceSearchBundle;
        boolean firstPage = true;

        IQuery healthcareServiceQuery = fhirClient.search().forResource(HealthcareService.class)
                .where(new ReferenceClientParam("organization").hasId(organizationResourceId))
                .where(new ReferenceClientParam("location").hasId(locationId));

        //Check for healthcare service status
        if (statusList.isPresent() && statusList.get().size() == 1) {
            log.info("Searching for healthcare service with the following specific status" + statusList.get().get(0) + " for the given OrganizationID:" + organizationResourceId);
            statusList.get().forEach(log::info);
            if (statusList.get().get(0).trim().equalsIgnoreCase("active")
                    || statusList.get().get(0).trim().equalsIgnoreCase("true")) {
                healthcareServiceQuery.where(new TokenClientParam("active").exactly().codes("true"));
            } else if (statusList.get().get(0).trim().equalsIgnoreCase("inactive")
                    || statusList.get().get(0).trim().equalsIgnoreCase("false")) {
                healthcareServiceQuery.where(new TokenClientParam("active").exactly().codes("false"));
            } else {
                log.info("Searching for healthcare services with ALL statuses for the given LocationID:" + locationId);
            }
        } else {
            log.info("Searching for healthcare services with ALL statuses for the given LocationID:" + locationId);
        }

        //Check for bad requests and additional criteria
        healthcareServiceQuery = addAdditionalSearchConditions(healthcareServiceQuery, searchKey, searchValue);

        //The following bundle only contains page 1 of the resultset
        firstPageHealthcareServiceSearchBundle = PaginationUtil.getSearchBundleFirstPage(healthcareServiceQuery, numberOfHealthcareServicesPerPage, Optional.of(HealthcareService.INCLUDE_LOCATION));

        if (firstPageHealthcareServiceSearchBundle == null || firstPageHealthcareServiceSearchBundle.getEntry().isEmpty()) {
            log.info("No Healthcare Service found for the given OrganizationID:" + organizationResourceId);
            return new PageDto<>(new ArrayList<>(), numberOfHealthcareServicesPerPage, 0, 0, 0, 0);
        }

        log.info("FHIR Healthcare Service(s) bundle retrieved " + firstPageHealthcareServiceSearchBundle.getTotal() + " healthcare service(s) from FHIR server successfully");

        otherPageHealthcareServiceSearchBundle = firstPageHealthcareServiceSearchBundle;
        if (pageNumber.isPresent() && pageNumber.get() > 1) {
            //Load the required page
            firstPage = false;
            otherPageHealthcareServiceSearchBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, otherPageHealthcareServiceSearchBundle, pageNumber.get(), numberOfHealthcareServicesPerPage);
        }

        List<Bundle.BundleEntryComponent> retrivedHealthcareServices = otherPageHealthcareServiceSearchBundle.getEntry();

        //Arrange Page related info
        List<HealthcareServiceDto> healthcareServicesList = retrivedHealthcareServices.stream()
                .filter(retrivedHealthcareService -> retrivedHealthcareService.getResource().getResourceType().equals(ResourceType.HealthcareService)).map(hcs -> {
                    HealthcareService healthcareServiceResource = (HealthcareService) hcs.getResource();
                    HealthcareServiceDto healthcareServiceDto = modelMapper.map(healthcareServiceResource, HealthcareServiceDto.class);
                    healthcareServiceDto.setLogicalId(hcs.getResource().getIdElement().getIdPart());
                    healthcareServiceDto.setOrganizationId(organizationResourceId);

                    //Getting location
                    List<NameLogicalIdIdentifiersDto> locationsForHealthService = new ArrayList<>();
                    healthcareServiceResource.getLocation().forEach(location -> {

                                if (location.getReference() != null && !location.getReference().isEmpty()) {
                                    String locationReference = location.getReference();
                                    String locationResourceId = locationReference.split("/")[1];
                                    String locationType = locationReference.split("/")[0];

                                    retrivedHealthcareServices.forEach(healthcareServiceWithLocation -> {
                                        Resource resource = healthcareServiceWithLocation.getResource();
                                        if (resource.getResourceType().toString().trim().replaceAll(" ", "").equalsIgnoreCase(locationType.trim().replaceAll(" ", ""))) {
                                            if (resource.getIdElement().getIdPart().equalsIgnoreCase(locationResourceId)) {
                                                Location locationPresent = (Location) resource;
                                                NameLogicalIdIdentifiersDto locationForHealthServiceDto = modelMapper.map(locationPresent, NameLogicalIdIdentifiersDto.class);
                                                locationForHealthServiceDto.setLogicalId(resource.getIdElement().getIdPart());
                                                locationsForHealthService.add(locationForHealthServiceDto);

                                                if (locationResourceId.equalsIgnoreCase(locationId)) {
                                                    healthcareServiceDto.setLocationId(locationId);
                                                    healthcareServiceDto.setLocationName(locationPresent.getName());
                                                }
                                            }
                                        }
                                    });
                                }
                                healthcareServiceDto.setLocation(locationsForHealthService);
                            }
                    );

                    return healthcareServiceDto;
                }).collect(Collectors.toList());

        double totalPages = Math.ceil((double) otherPageHealthcareServiceSearchBundle.getTotal() / numberOfHealthcareServicesPerPage);
        int currentPage = firstPage ? 1 : pageNumber.get();

        return new PageDto<>(healthcareServicesList, numberOfHealthcareServicesPerPage, totalPages, currentPage, healthcareServicesList.size(), otherPageHealthcareServiceSearchBundle.getTotal());
    }

    @Override
    public HealthcareServiceDto getHealthcareService(String healthcareServiceId) {
        log.info("Searching for Healthcare Service Id:" + healthcareServiceId);
        Map<String, String> locationNameMap = new HashMap<>();

        Bundle healthcareServiceBundle = fhirClient.search().forResource(HealthcareService.class)
                .where(new TokenClientParam("_id").exactly().code(healthcareServiceId))
                .returnBundle(Bundle.class)
                .execute();

        if (healthcareServiceBundle == null || healthcareServiceBundle.getEntry().isEmpty()) {
            log.info("No healthcare service was found for the given Healthcare Service ID:" + healthcareServiceId);
            throw new ResourceNotFoundException("No healthcare service was found for the given Healthcare Service ID:" + healthcareServiceId);
        }

        log.info("FHIR Healthcare Service bundle retrieved from FHIR server successfully for Healthcare Service Id:" + healthcareServiceId);

        Bundle.BundleEntryComponent retrievedHealthcareService = healthcareServiceBundle.getEntry().get(0);

        return convertHealthcareServiceBundleEntryToHealthcareServiceDto(retrievedHealthcareService, locationNameMap, Optional.empty());
    }

    @Override
    public void createHealthcareService(String organizationId, HealthcareServiceDto healthcareServiceDto) {
        log.info("Creating Healthcare Service for Organization Id:" + organizationId);
        log.info("But first, checking if a duplicate Healthcare Service exists based on the Identifiers provided.");

        checkForDuplicateHealthcareServiceBasedOnTypesDuringCreate(healthcareServiceDto, organizationId);

        HealthcareService fhirHealthcareService = modelMapper.map(healthcareServiceDto, HealthcareService.class);
        fhirHealthcareService.setActive(Boolean.TRUE);
        fhirHealthcareService.setProvidedBy(new Reference("Organization/" + organizationId.trim()));

        // Validate
        FhirUtil.validateFhirResource(fhirValidator, fhirHealthcareService, Optional.empty(), ResourceType.HealthcareService.name(), "Create Healthcare Service");

        //Create
        FhirUtil.createFhirResource(fhirClient, fhirHealthcareService, ResourceType.HealthcareService.name());
    }

    @Override
    public void updateHealthcareService(String organizationId, String healthcareServiceId, HealthcareServiceDto healthcareServiceDto) {
        log.info("Updating healthcareService Id: " + healthcareServiceId + " for Organization Id:" + organizationId);
        log.info("But first, checking if a duplicate healthcareService(active/inactive/suspended) exists based on the Identifiers provided.");
        checkForDuplicateHealthcareServiceBasedOnTypesDuringUpdate(healthcareServiceDto, healthcareServiceId, organizationId);

        //First, get the existing resource from the server
        HealthcareService existingHealthcareService = readHealthcareServiceFromServer(healthcareServiceId);

        HealthcareService updatedHealthcareService = modelMapper.map(healthcareServiceDto, HealthcareService.class);

        //Overwrite values from the dto
        existingHealthcareService.setName(updatedHealthcareService.getName());
        existingHealthcareService.setProgramName(updatedHealthcareService.getProgramName());
        existingHealthcareService.setCategory(updatedHealthcareService.getCategory());
        existingHealthcareService.setType(updatedHealthcareService.getType());
        existingHealthcareService.setSpecialty(updatedHealthcareService.getSpecialty());
        existingHealthcareService.setReferralMethod(updatedHealthcareService.getReferralMethod());
        existingHealthcareService.setTelecom(updatedHealthcareService.getTelecom());
        if (updatedHealthcareService.getActive()) {
            existingHealthcareService.setActive(updatedHealthcareService.getActive());
        } else {
            //Remove all locations
            existingHealthcareService.setActive(updatedHealthcareService.getActive());
            existingHealthcareService.setLocation(null);
        }

        // Validate
        FhirUtil.validateFhirResource(fhirValidator, existingHealthcareService, Optional.of(healthcareServiceId), ResourceType.HealthcareService.name(), "Update Healthcare Service");

        //Update
        FhirUtil.updateFhirResource(fhirClient, existingHealthcareService, "Update Healthcare Service");
    }

    @Override
    public void inactivateHealthcareService(String healthcareServiceId) {
        log.info("Inactivating the healthcareServiceId Id: " + healthcareServiceId);
        HealthcareService existingHealthcareService = readHealthcareServiceFromServer(healthcareServiceId);
        existingHealthcareService.setActive(false);
        //Also, remove all locations
        existingHealthcareService.setLocation(null);
        //Update
        FhirUtil.updateFhirResource(fhirClient, existingHealthcareService, "Inactivate Healthcare Service");
    }

    @Override
    public void assignLocationsToHealthcareService(String healthcareServiceId, String organizationResourceId, List<String> locationIdList) {
        boolean allChecksPassed = false;

        //First, validate if the given location(s) belong to the given organization Id
        Bundle locationSearchBundle = getLocationBundle(organizationResourceId);

        if (locationSearchBundle == null || locationSearchBundle.getEntry().isEmpty()) {
            log.info("Assign location to a HealthcareService: No location found for the given organization ID:" + organizationResourceId);
            throw new ResourceNotFoundException("Cannot assign the given location(s) to Healthcare Service, because we did not find any location(s) under the organization ID: " + organizationResourceId);
        }

        List<String> retrievedLocationsList = locationSearchBundle.getEntry().stream().map(fhirLocationModel -> fhirLocationModel.getResource().getIdElement().getIdPart()).collect(Collectors.toList());

        if (retrievedLocationsList.containsAll(locationIdList)) {
            log.info("Assign location to a Healthcare Service: Successful Check 1: The given location(s) belong to the given organization ID: " + organizationResourceId);

            HealthcareService existingHealthcareService = readHealthcareServiceFromServer(healthcareServiceId);
            List<Reference> assignedLocations = existingHealthcareService.getLocation();

            //Next, avoid adding redundant location data
            Set<String> existingAssignedLocations = assignedLocations.stream().map(locReference -> locReference.getReference().substring(9)).collect(Collectors.toSet());
            locationIdList.removeIf(existingAssignedLocations::contains);

            if (locationIdList.isEmpty()) {
                log.info("Assign location to a Healthcare Service: All location(s) from the query params have already been assigned to belonged to healthcare Service ID:" + healthcareServiceId + ". Nothing to do!");
            } else {
                log.info("Assign location to a Healthcare Service: Successful Check 2: Found some location(s) from the query params that CAN be assigned to belonged to healthcare Service ID:" + healthcareServiceId);
                allChecksPassed = true;
            }

            if (allChecksPassed) {
                locationIdList.forEach(locationId -> assignedLocations.add(new Reference("Location/" + locationId)));

                //Validate
                FhirUtil.validateFhirResource(fhirValidator, existingHealthcareService, Optional.of(healthcareServiceId), ResourceType.HealthcareService.name(), "Assign location to a Healthcare Service");

                //Update
                FhirUtil.updateFhirResource(fhirClient, existingHealthcareService, "Assign Location to a Healthcare Service");
            }
        } else {
            throw new BadRequestException("Cannot assign the given location(s) to Healthcare Service, because not all location(s) from the query params belonged to the organization ID: " + organizationResourceId);
        }
    }

    @Override
    public void unassignLocationsFromHealthcareService(String healthcareServiceId, String organizationResourceId, List<String> locationIdList) {
        HealthcareService existingHealthcareService = readHealthcareServiceFromServer(healthcareServiceId);
        List<Reference> assignedLocations = existingHealthcareService.getLocation();
        assignedLocations.removeIf(locRef -> locationIdList.contains(locRef.getReference().substring(9).trim()));
        //Validate
        FhirUtil.validateFhirResource(fhirValidator, existingHealthcareService, Optional.of(healthcareServiceId), ResourceType.HealthcareService.name(), "Unassign location to a Healthcare Service");

        //Update
        FhirUtil.updateFhirResource(fhirClient, existingHealthcareService, "Unassign Location to a Healthcare Service");
    }


    private HealthcareService readHealthcareServiceFromServer(String healthcareServiceId) {
        HealthcareService existingHealthcareService;

        try {
            existingHealthcareService = fhirClient.read().resource(HealthcareService.class).withId(healthcareServiceId.trim()).execute();
        }
        catch (BaseServerResponseException e) {
            log.error("FHIR Client returned with an error while reading the Healthcare Service with ID: " + healthcareServiceId);
            throw new ResourceNotFoundException("FHIR Client returned with an error while reading the Healthcare Service: " + e.getMessage());
        }
        return existingHealthcareService;
    }

    private Bundle getLocationBundle(String organizationResourceId) {
        IQuery locationsSearchQuery = fhirClient.search().forResource(Location.class).where(new ReferenceClientParam("organization").hasId(organizationResourceId.trim()));

        return (Bundle) locationsSearchQuery.count(1000)
                .returnBundle(Bundle.class)
                .encodedJson()
                .execute();
    }

    private HealthcareServiceDto convertHealthcareServiceBundleEntryToHealthcareServiceDto(Bundle.BundleEntryComponent fhirHealthcareServiceModel, Map<String, String> locationNameMap, Optional<String> assignedToLocationId) {
        HealthcareServiceDto tempHealthcareServiceDto = modelMapper.map(fhirHealthcareServiceModel.getResource(), HealthcareServiceDto.class);
        tempHealthcareServiceDto.setLogicalId(fhirHealthcareServiceModel.getResource().getIdElement().getIdPart());
        HealthcareService hcs = (HealthcareService) fhirHealthcareServiceModel.getResource();
        List<Reference> locationRefList = hcs.getLocation();
        List<NameLogicalIdIdentifiersDto> locNameList = new ArrayList<>();
        Set<String> locIdSet = new HashSet<>();

        for (Reference locRef : locationRefList) {
            if (locRef.getReference() != null) {
                String locLogicalId = locRef.getReference().substring(9).trim();
                String locName;
                //First, check in Map if name Exists
                if (locationNameMap.containsKey(locLogicalId)) {
                    locName = locationNameMap.get(locLogicalId);
                } else {
                    //If not, Check If there is Display element for this location
                    if (locRef.getDisplay() != null) {
                        locName = locRef.getDisplay().trim();
                    } else {
                        //If not(last option), read from FHIR server
                        try {
                            Location locationFromServer = fhirClient.read().resource(Location.class).withId(locLogicalId.trim()).execute();
                            locName = locationFromServer.getName().trim();
                        }
                        catch (BaseServerResponseException e) {
                            log.error("FHIR Client returned with an error while reading the location with ID: " + locLogicalId);
                            throw new ResourceNotFoundException("FHIR Client returned with an error while reading the location:" + e.getMessage());
                        }
                    }
                }
                //Add to map
                locationNameMap.put(locLogicalId, locName);
                locIdSet.add(locLogicalId);

                //Add locations list to the dto
                NameLogicalIdIdentifiersDto tempIdName = new NameLogicalIdIdentifiersDto();
                tempIdName.setLogicalId(locLogicalId);
                tempIdName.setName(locName);
                locNameList.add(tempIdName);
            }
        }

        tempHealthcareServiceDto.setLocation(locNameList);

        if (assignedToLocationId.isPresent() && locIdSet.contains(assignedToLocationId.get())) {
            tempHealthcareServiceDto.setAssignedToCurrentLocation(true);
        } else if (assignedToLocationId.isPresent() && !locIdSet.contains(assignedToLocationId.get())) {
            tempHealthcareServiceDto.setAssignedToCurrentLocation(false);
        }

        return tempHealthcareServiceDto;
    }

    private void checkForDuplicateHealthcareServiceBasedOnTypesDuringCreate(HealthcareServiceDto healthcareServiceDto, String organizationId) {
        List<ValueSetDto> typeList = healthcareServiceDto.getType();
        log.info("Current HealthcareServiceDto has " + typeList.size() + " service type(s).");

        ValueSetDto serviceCategory = healthcareServiceDto.getCategory();
        String categorySystem = serviceCategory.getSystem();
        String categoryCode = serviceCategory.getCode();


        for (ValueSetDto tempType : typeList) {
            String typeSystem = tempType.getSystem();
            String typeCode = tempType.getCode();
            checkDuplicateHealthcareServiceExistsDuringCreate(organizationId, categorySystem, categoryCode, typeSystem, typeCode);
        }
        log.info("Create Healthcare Service: Found no duplicate Healthcare service.");
    }

    private void checkDuplicateHealthcareServiceExistsDuringCreate(String organizationId, String categorySystem, String categoryCode, String typeSystem, String typeCode) {
        Bundle bundle = getHealthCareServiceBundleBasedOnCategoryAndType(organizationId, categorySystem, categoryCode, typeSystem, typeCode);

        if (bundle != null && !bundle.getEntry().isEmpty()) {
            throw new DuplicateResourceFoundException("The current organization: " + organizationId + " already has active Healthcare Service(s) with the Category System " + categorySystem + " and Category Code " + categoryCode + " with Type system " + typeSystem + " and Type Code: " + typeCode);
        }
    }

    private void checkForDuplicateHealthcareServiceBasedOnTypesDuringUpdate(HealthcareServiceDto healthcareServiceDto, String healthcareServiceId, String organizationId) {
        List<ValueSetDto> typeList = healthcareServiceDto.getType();
        log.info("Update Healthcare Service: Current HealthcareServiceDto has " + typeList.size() + " service type(s).");

        ValueSetDto serviceCategory = healthcareServiceDto.getCategory();
        String categorySystem = serviceCategory.getSystem();
        String categoryCode = serviceCategory.getCode();

        for (ValueSetDto tempType : typeList) {
            String typeSystem = tempType.getSystem();
            String typeCode = tempType.getCode();
            checkDuplicateHealthcareServiceExistsDuringUpdate(organizationId, healthcareServiceId, categorySystem, categoryCode, typeSystem, typeCode);
        }
        log.info("Update Healthcare Service: Found no duplicate Healthcare service");

    }

    private void checkDuplicateHealthcareServiceExistsDuringUpdate(String organizationId, String healthcareServiceId, String categorySystem, String categoryCode, String typeSystem, String typeCode) {
        Bundle bundle = getHealthCareServiceBundleBasedOnCategoryAndType(organizationId, categorySystem, categoryCode, typeSystem, typeCode);

        if (bundle != null && bundle.getEntry().size() > 1) {
            throw new DuplicateResourceFoundException("A Healthcare Service already exists");
        } else if (bundle != null && bundle.getEntry().size() == 1) {
            String logicalId = bundle.getEntry().get(0).getResource().getIdElement().getIdPart();
            if (!healthcareServiceId.trim().equalsIgnoreCase(logicalId.trim())) {
                throw new DuplicateResourceFoundException("A Healthcare Service already exists");
            }
        }
    }

    private Bundle getHealthCareServiceBundleBasedOnCategoryAndType(String organizationId, String categorySystem, String categoryCode, String typeSystem, String typeCode) {
        Bundle bundle;
        IQuery<IBaseBundle> healthcareServicesSearchQuery = fhirClient.search().forResource(HealthcareService.class)
                .where(new ReferenceClientParam("organization").hasId(organizationId))
                .and(new TokenClientParam("active").exactly().code("true"));

        if (categorySystem != null && !categorySystem.trim().isEmpty()
                && categoryCode != null && !categoryCode.trim().isEmpty()) {
            healthcareServicesSearchQuery.and(new TokenClientParam("category").exactly().systemAndCode(categorySystem.trim(), categoryCode.trim()));
        } else if (categoryCode != null && !categoryCode.trim().isEmpty()) {
            healthcareServicesSearchQuery.and(new TokenClientParam("category").exactly().code(categoryCode.trim()));
        } else {
            throw new BadRequestException("Found no valid Category System and/or Code");
        }

        if (typeSystem != null && !typeSystem.trim().isEmpty()
                && typeCode != null && !typeCode.trim().isEmpty()) {
            healthcareServicesSearchQuery.and(new TokenClientParam("type").exactly().systemAndCode(typeSystem.trim(), typeCode.trim()));
        } else if (typeCode != null && !typeCode.trim().isEmpty()) {
            healthcareServicesSearchQuery.and(new TokenClientParam("type").exactly().code(typeCode.trim()));
        } else {
            throw new BadRequestException("Found no valid Type System and/or Code");
        }

        bundle = healthcareServicesSearchQuery.returnBundle(Bundle.class).execute();
        return bundle;
    }


    private IQuery addAdditionalSearchConditions(IQuery healthcareServicesSearchQuery, Optional<String> searchKey, Optional<String> searchValue) {
        if (searchKey.isPresent() && !SearchKeyEnum.HealthcareServiceSearchKey.contains(searchKey.get())) {
            throw new BadRequestException("Unidentified search key:" + searchKey.get());
        } else if ((searchKey.isPresent() && !searchValue.isPresent()) ||
                (searchKey.isPresent() && searchValue.get().trim().isEmpty())) {
            throw new BadRequestException("No search value found for the search key" + searchKey.get());
        }

        // Check if there are any additional search criteria
        if (searchKey.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.HealthcareServiceSearchKey.NAME.name())) {
            log.info("Searching for healthcare service " + SearchKeyEnum.HealthcareServiceSearchKey.NAME.name() + " = " + searchValue.get().trim());
            healthcareServicesSearchQuery.where(new StringClientParam("name").matches().value(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.HealthcareServiceSearchKey.LOGICALID.name())) {
            log.info("Searching for healthcare service " + SearchKeyEnum.HealthcareServiceSearchKey.LOGICALID.name() + " = " + searchValue.get().trim());
            healthcareServicesSearchQuery.where(new TokenClientParam("_id").exactly().code(searchValue.get().trim()));
        } else if (searchKey.isPresent() && searchKey.get().equalsIgnoreCase(SearchKeyEnum.HealthcareServiceSearchKey.IDENTIFIERVALUE.name())) {
            log.info("Searching for healthcare service " + SearchKeyEnum.HealthcareServiceSearchKey.IDENTIFIERVALUE.name() + " = " + searchValue.get().trim());
            healthcareServicesSearchQuery.where(new TokenClientParam("identifier").exactly().code(searchValue.get().trim()));
        } else {
            log.info("Search healthcare service : No additional search criteria entered.");
        }
        return healthcareServicesSearchQuery;
    }
}

