package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.PractitionerDto;
import gov.samhsa.ocp.ocpfis.service.exception.DuplicateResourceFoundException;
import gov.samhsa.ocp.ocpfis.service.exception.PractitionerNotFoundException;
import gov.samhsa.ocp.ocpfis.web.PractitionerController;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.PractitionerRole;
import org.hl7.fhir.dstu3.model.Reference;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@Slf4j
public class PractitionerServiceImpl implements PractitionerService {

    private final ModelMapper modelMapper;

    private final IGenericClient fhirClient;

    private final FisProperties fisProperties;

    @Autowired
    public PractitionerServiceImpl(ModelMapper modelMapper, IGenericClient fhirClient, FisProperties fisProperties) {
        this.modelMapper = modelMapper;
        this.fhirClient = fhirClient;
        this.fisProperties = fisProperties;
    }

    @Override
    public PageDto<PractitionerDto> getAllPractitioners(Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size) {
        int numberOfPractitionersPerPage = size.filter(s -> s > 0 &&
                s <= fisProperties.getPractitioner().getPagination().getMaxSize()).orElse(fisProperties.getPractitioner().getPagination().getDefaultSize());

        boolean firstPage = true;


        IQuery practitionerIQuery = fhirClient.search().forResource(Practitioner.class);

        if (showInactive.isPresent()) {
            if (!showInactive.get())
                practitionerIQuery.where(new TokenClientParam("active").exactly().code("true"));
        } else {
            practitionerIQuery.where(new TokenClientParam("active").exactly().code("true"));
        }

        Bundle firstPagePractitionerBundle;
        Bundle otherPagePractitionerBundle;

        firstPagePractitionerBundle = (Bundle) practitionerIQuery
                .count(numberOfPractitionersPerPage)
                .returnBundle(Bundle.class)
                .execute();

        if (firstPagePractitionerBundle == null || firstPagePractitionerBundle.getEntry().size() < 1) {
            throw new PractitionerNotFoundException("No practitioners were found in the FHIR server");
        }

        otherPagePractitionerBundle = firstPagePractitionerBundle;

        if (page.isPresent() && page.get() > 1 && otherPagePractitionerBundle.getLink(Bundle.LINK_NEXT) != null) {
            // Load the required page
            firstPage = false;
            otherPagePractitionerBundle = getPractitionerSearchBundleAfterFirstPage(firstPagePractitionerBundle, page.get(), numberOfPractitionersPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedPractitioners = otherPagePractitionerBundle.getEntry();

        return practitionersInPage(retrievedPractitioners, otherPagePractitionerBundle, numberOfPractitionersPerPage, firstPage, page);

    }


    @Override
    public PageDto<PractitionerDto> searchPractitioners(PractitionerController.SearchType type, String value, Optional<Boolean> showInactive, Optional<Integer> page, Optional<Integer> size) {
        int numberOfPractitionersPerPage = size.filter(s -> s > 0 &&
                s <= fisProperties.getPractitioner().getPagination().getMaxSize()).orElse(fisProperties.getPractitioner().getPagination().getDefaultSize());

        IQuery practitionerIQuery = fhirClient.search().forResource(Practitioner.class);
        boolean firstPage = true;

        if (type.equals(PractitionerController.SearchType.name))
            practitionerIQuery.where(new StringClientParam("name").matches().value(value.trim()));

        if (type.equals(PractitionerController.SearchType.identifier))
            practitionerIQuery.where(new TokenClientParam("identifier").exactly().code(value.trim()));

        if (showInactive.isPresent()) {
            if (!showInactive.get())
                practitionerIQuery.where(new TokenClientParam("active").exactly().code("true"));
        } else {
            practitionerIQuery.where(new TokenClientParam("active").exactly().code("true"));
        }

        Bundle firstPagePractitionerSearchBundle;
        Bundle otherPagePractitionerSearchBundle;

        firstPagePractitionerSearchBundle = (Bundle) practitionerIQuery.count(numberOfPractitionersPerPage).returnBundle(Bundle.class)
                .execute();

        if (firstPagePractitionerSearchBundle == null || firstPagePractitionerSearchBundle.isEmpty() || firstPagePractitionerSearchBundle.getEntry().size() < 1) {
            throw new PractitionerNotFoundException("No practitioners were found in the FHIR server.");
        }

        otherPagePractitionerSearchBundle = firstPagePractitionerSearchBundle;

        if (page.isPresent() && page.get() > 1 && otherPagePractitionerSearchBundle.getLink(Bundle.LINK_NEXT) != null) {
            firstPage = false;
            otherPagePractitionerSearchBundle = getPractitionerSearchBundleAfterFirstPage(firstPagePractitionerSearchBundle, page.get(), numberOfPractitionersPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedPractitioners = otherPagePractitionerSearchBundle.getEntry();

        return practitionersInPage(retrievedPractitioners, otherPagePractitionerSearchBundle, numberOfPractitionersPerPage, firstPage, page);
    }


    public void createPractitioner(PractitionerDto practitionerDto) {
        //Check Duplicate Identifier
        boolean hasDuplicateIdentifier = practitionerDto.getIdentifiers().stream().anyMatch(identifierDto -> {
            if (fhirClient.search()
                    .forResource(Practitioner.class)
                    .where(new TokenClientParam("identifier")
                            .exactly().systemAndCode(identifierDto.getSystem(), identifierDto.getValue()))
                    .returnBundle(Bundle.class).execute().getTotal() > 0) {
                return true;
            }
            return false;
        });

        //When there is no duplicate identifier, practitioner gets created
        if (!hasDuplicateIdentifier) {
            //Create Fhir Practitioner
            Practitioner practitioner = modelMapper.map(practitionerDto, Practitioner.class);
            MethodOutcome methodOutcome = fhirClient.create().resource(practitioner).execute();

            //Create PractitionerRole for the practitioner
            PractitionerRole practitionerRole = new PractitionerRole();
            practitionerDto.getPractitionerRoles().stream().forEach(practitionerRoleCode -> {
                        //Assign fhir practitionerRole codes.
                        CodeableConcept codeableConcept = new CodeableConcept();
                        codeableConcept.setText(practitionerRoleCode.getCode());
                        practitionerRole.addCode(codeableConcept);
                    }
            );

            //Assign fhir Practitioner resource id.
            Reference practitionerId = new Reference();
            practitionerId.setReference("Practitioner/" + methodOutcome.getId().getIdPart());
            practitionerRole.setPractitioner(practitionerId);

            fhirClient.create().resource(practitionerRole).execute();
        } else {
            throw new DuplicateResourceFoundException("Practitioner with the same Identifier is already present.");
        }
    }

    @Override
    public void updatePractitioner(PractitionerDto practitionerDto) {
        //Getting resource id from resource URL and setting it to practitioner.
        String resourceId = practitionerDto.getResourceURL().replace("http://fhirtest.uhn.ca/baseDstu3/Practitioner/", "");
        resourceId = resourceId.split("/")[0];

        //Check Duplicate Identifier
        String finalResourceId = resourceId;
        boolean hasDuplicateIdentifier = practitionerDto.getIdentifiers().stream().anyMatch(identifierDto -> {
            IQuery practitionersWithUpdatedIdentifierQuery = fhirClient.search()
                    .forResource(Practitioner.class)
                    .where(new TokenClientParam("identifier")
                            .exactly().systemAndCode(identifierDto.getSystem(), identifierDto.getValue()));

            Bundle practitionerWithUpdatedIdentifierBundle = (Bundle) practitionersWithUpdatedIdentifierQuery.returnBundle(Bundle.class).execute();
            Bundle practitionerWithUpdatedIdentifierAndSameResourceIdBundle = (Bundle) practitionersWithUpdatedIdentifierQuery.where(new TokenClientParam("_id").exactly().code(finalResourceId)).returnBundle(Bundle.class).execute();
            if (practitionerWithUpdatedIdentifierBundle.getTotal() > 0) {
                if (practitionerWithUpdatedIdentifierBundle.getTotal() == practitionerWithUpdatedIdentifierAndSameResourceIdBundle.getTotal()) {
                    return false;
                } else {
                    return true;
                }
            }
            return false;
        });

        if (!hasDuplicateIdentifier) {
            Practitioner practitioner = modelMapper.map(practitionerDto, Practitioner.class);

            practitioner.setId(new IdType(finalResourceId));

            MethodOutcome methodOutcome = fhirClient.update().resource(practitioner)
                    .execute();

            //Update PractitionerRole for the practitioner
            PractitionerRole practitionerRole = new PractitionerRole();
            practitionerDto.getPractitionerRoles().stream().forEach(practitionerRoleCode -> {
                        //Assign fhir practitionerRole codes.
                        CodeableConcept codeableConcept = new CodeableConcept();
                        codeableConcept.setText(practitionerRoleCode.getCode());
                        practitionerRole.addCode(codeableConcept);
                    }
            );

            //Assign fhir Practitioner resource id.
            Reference practitionerId = new Reference();
            practitionerId.setReference("Practitioner/" + methodOutcome.getId().getIdPart());

            practitionerRole.setPractitioner(practitionerId);

            fhirClient.update().resource(practitionerRole).conditional()
                    .where(new ReferenceClientParam("practitioner")
                            .hasId("Practitioner/" + methodOutcome.getId().getIdPart())).execute();
        } else {
            throw new DuplicateResourceFoundException("Practitioner with the same Identifier is already present");
        }
    }


    private Bundle getPractitionerSearchBundleAfterFirstPage(Bundle practitionerSearchBundle, int page, int size) {
        if (practitionerSearchBundle.getLink(Bundle.LINK_NEXT) != null) {
            //Assuming page number starts with 1
            int offset = ((page >= 1 ? page : 1) - 1) * size;

            if (offset >= practitionerSearchBundle.getTotal()) {
                throw new PractitionerNotFoundException("No practitioners were found in the FHIR server for this page number");
            }

            String pageUrl = fisProperties.getFhir().getServerUrl()
                    + "?_getpages=" + practitionerSearchBundle.getId()
                    + "&_getpagesoffset=" + offset
                    + "&_count=" + size
                    + "&_bundletype=searchset";

            // Load the required page
            return fhirClient.search().byUrl(pageUrl)
                    .returnBundle(Bundle.class)
                    .execute();
        }
        return practitionerSearchBundle;
    }

    private PageDto<PractitionerDto> practitionersInPage(List<Bundle.BundleEntryComponent> retrievedPractitioners, Bundle otherPagePractitionerBundle, int numberOfPractitionersPerPage, boolean firstPage, Optional<Integer> page) {
        List<PractitionerDto> practitionersList = retrievedPractitioners.stream().map(retrievedPractitioner -> modelMapper.map(retrievedPractitioner.getResource(), PractitionerDto.class)).collect(Collectors.toList());
        double totalPages = Math.ceil((double) otherPagePractitionerBundle.getTotal() / numberOfPractitionersPerPage);
        int currentPage = firstPage ? 1 : page.get();

        return new PageDto<>(practitionersList, numberOfPractitionersPerPage, totalPages, currentPage, practitionersList.size(),
                otherPagePractitionerBundle.getTotal());
    }

}
