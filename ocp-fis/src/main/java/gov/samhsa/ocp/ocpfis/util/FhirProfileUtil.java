package gov.samhsa.ocp.ocpfis.util;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.ActivityDefinition;
import org.hl7.fhir.dstu3.model.Appointment;
import org.hl7.fhir.dstu3.model.CareTeam;
import org.hl7.fhir.dstu3.model.Communication;
import org.hl7.fhir.dstu3.model.Consent;
import org.hl7.fhir.dstu3.model.Coverage;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Flag;
import org.hl7.fhir.dstu3.model.HealthcareService;
import org.hl7.fhir.dstu3.model.Location;
import org.hl7.fhir.dstu3.model.Meta;
import org.hl7.fhir.dstu3.model.Organization;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Practitioner;
import org.hl7.fhir.dstu3.model.PractitionerRole;
import org.hl7.fhir.dstu3.model.RelatedPerson;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.Task;
import org.hl7.fhir.dstu3.model.UriType;

import java.util.List;

@Slf4j
public class FhirProfileUtil {

    public static void setAppointmentProfileMetaData(IGenericClient fhirClient, Appointment appointment) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Appointment.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            appointment.setMeta(meta);
        }
    }

    public static void setCareTeamProfileMetaData(IGenericClient fhirClient, CareTeam careTeam) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.CareTeam.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            careTeam.setMeta(meta);
        }
    }

    public static void setRelatedPersonProfileMetaData(IGenericClient fhirClient, RelatedPerson relatedPerson) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.RelatedPerson.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            relatedPerson.setMeta(meta);
        }
    }

    public static void setHealthCareServiceProfileMetaData(IGenericClient fhirClient, HealthcareService healthcareService){
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.HealthcareService.toString());
        if(uriList !=null && !uriList.isEmpty()){
            Meta meta = new Meta().setProfile(uriList);
            healthcareService.setMeta(meta);
        }
    }

    public static void setLocationProfileMetaData(IGenericClient fhirClient, Location fhirLocation) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Location.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            fhirLocation.setMeta(meta);
        }
    }

    public static void setOrganizationProfileMetaData(IGenericClient fhirClient, Organization organization) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Organization.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            organization.setMeta(meta);
        }
    }

    public static void setActivityDefinitionProfileMetaData(IGenericClient fhirClient, ActivityDefinition activityDefinition) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.ActivityDefinition.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            activityDefinition.setMeta(meta);
        }
    }

    public static void setPractitionerProfileMetaData(IGenericClient fhirClient, Practitioner practitioner) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Practitioner.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            practitioner.setMeta(meta);
        }
    }

    public static void setPractitionerRoleProfileMetaData(IGenericClient fhirClient, PractitionerRole practitionerRole) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.PractitionerRole.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            practitionerRole.setMeta(meta);
        }
    }

    public static void setCommunicationProfileMetaData(IGenericClient fhirClient, Communication communication) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Communication.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            communication.setMeta(meta);
        }
    }

    public static void setTaskProfileMetaData(IGenericClient fhirClient, Task task) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Task.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            task.setMeta(meta);
        }
    }

    public static void setCoverageProfileMetaData(IGenericClient fhirClient, Coverage coverage) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Coverage.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            coverage.setMeta(meta);
        }
    }

    public static void setConsentProfileMetaData(IGenericClient fhirClient, Consent consent) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Consent.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            consent.setMeta(meta);
        }
    }

    public static void setPatientProfileMetaData(IGenericClient fhirClient, Patient patient) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Patient.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            patient.setMeta(meta);
        }
    }

    public static void setFlagProfileMetaData(IGenericClient fhirClient, Flag flag) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.Flag.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            flag.setMeta(meta);
        }
    }

    public static void setEpisodeOfCareProfileMetaData(IGenericClient fhirClient, EpisodeOfCare episodeOfCare) {
        List<UriType> uriList = FhirOperationUtil.getURIList(fhirClient, ResourceType.EpisodeOfCare.toString());
        if (uriList != null && !uriList.isEmpty()) {
            Meta meta = new Meta().setProfile(uriList);
            episodeOfCare.setMeta(meta);
        }
    }
}
