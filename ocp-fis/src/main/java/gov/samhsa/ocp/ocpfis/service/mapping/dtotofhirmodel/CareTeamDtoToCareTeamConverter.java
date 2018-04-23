package gov.samhsa.ocp.ocpfis.service.mapping.dtotofhirmodel;

import gov.samhsa.ocp.ocpfis.domain.ParticipantTypeEnum;
import gov.samhsa.ocp.ocpfis.service.dto.CareTeamDto;
import gov.samhsa.ocp.ocpfis.service.dto.ParticipantDto;
import gov.samhsa.ocp.ocpfis.util.DateUtil;
import org.hl7.fhir.dstu3.model.CareTeam;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.exceptions.FHIRException;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CareTeamDtoToCareTeamConverter {

    public static CareTeam map(CareTeamDto careTeamDto) throws FHIRException, ParseException {
        CareTeam careTeam = new CareTeam();
        //id
        careTeam.setId(careTeamDto.getId());

        //name
        careTeam.setName(careTeamDto.getName());

        //status
        CareTeam.CareTeamStatus careTeamStatus = CareTeam.CareTeamStatus.fromCode(careTeamDto.getStatusCode());
        careTeam.setStatus(careTeamStatus);

        //categories
        Coding coding = new Coding();
        coding.setCode(careTeamDto.getCategoryCode());
        CodeableConcept codeableConcept = new CodeableConcept().addCoding(coding);
        careTeam.addCategory(codeableConcept);

        //subject
        careTeam.getSubject().setReference("Patient/" + careTeamDto.getSubjectId());

        //start and end date
        Period period = new Period();
        period.setStart(DateUtil.convertStringToDate(careTeamDto.getStartDate()));
        period.setEnd(DateUtil.convertStringToDate(careTeamDto.getEndDate()));
        careTeam.setPeriod(period);

        //ReasonCode
        //just supporting one reasonCode
        Coding codingReasonCode = new Coding();
        codingReasonCode.setCode(careTeamDto.getReasonCode());
        CodeableConcept codeableConceptReasonCode = new CodeableConcept().addCoding(codingReasonCode);
        careTeam.setReasonCode(Collections.singletonList(codeableConceptReasonCode));

        //participants
        List<ParticipantDto> participantDtoList = careTeamDto.getParticipants();
        List<CareTeam.CareTeamParticipantComponent> participantsList = new ArrayList<>();

        for(ParticipantDto participantDto : participantDtoList) {
            CareTeam.CareTeamParticipantComponent careTeamParticipant = new CareTeam.CareTeamParticipantComponent();

            String memberType = participantDto.getMemberType();

            if(memberType.equalsIgnoreCase(ParticipantTypeEnum.practitioner.getCode())) {
                careTeamParticipant.getMember().setReference(ParticipantTypeEnum.practitioner.getName() + "/" + participantDto.getMemberId());

            } else if (memberType.equalsIgnoreCase(ParticipantTypeEnum.patient.getCode())) {
                careTeamParticipant.getMember().setReference(ParticipantTypeEnum.patient.getName() + "/" + participantDto.getMemberId());

            } else if (memberType.equalsIgnoreCase(ParticipantTypeEnum.organization.getCode())) {
                careTeamParticipant.getMember().setReference(ParticipantTypeEnum.organization.getName() + "/" + participantDto.getMemberId());

            } else if (memberType.equalsIgnoreCase(ParticipantTypeEnum.relatedPerson.getCode())) {
                careTeamParticipant.getMember().setReference(ParticipantTypeEnum.relatedPerson.getName() + "/" + participantDto.getMemberId());
            }

            Coding codingRoleCode = new Coding();
            codingRoleCode.setCode(participantDto.getRoleCode());
            CodeableConcept codeableConceptRoleCode = new CodeableConcept().addCoding(codingRoleCode);
            careTeamParticipant.setRole(codeableConceptRoleCode);

            Period participantPeriod = new Period();
            participantPeriod.setStart(DateUtil.convertStringToDate(participantDto.getStartDate()));
            participantPeriod.setEnd(DateUtil.convertStringToDate(participantDto.getEndDate()));
            careTeamParticipant.setPeriod(participantPeriod);

            participantsList.add(careTeamParticipant);
        }


        careTeam.setParticipant(participantsList);

        //managingOrganization
        Reference reference = new Reference();
        reference.setReference(ResourceType.Organization + "/" + careTeamDto.getManagingOrganization());
        careTeam.setManagingOrganization(Arrays.asList(reference));

        return careTeam;
    }




}
