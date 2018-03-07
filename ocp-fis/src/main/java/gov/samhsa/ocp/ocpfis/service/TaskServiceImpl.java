package gov.samhsa.ocp.ocpfis.service;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.validation.FhirValidator;
import gov.samhsa.ocp.ocpfis.config.FisProperties;
import gov.samhsa.ocp.ocpfis.service.dto.ContextDto;
import gov.samhsa.ocp.ocpfis.service.dto.EpisodeOfCareDto;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.PeriodDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import gov.samhsa.ocp.ocpfis.service.dto.TaskDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import gov.samhsa.ocp.ocpfis.service.exception.DuplicateResourceFoundException;
import gov.samhsa.ocp.ocpfis.service.exception.ResourceNotFoundException;
import gov.samhsa.ocp.ocpfis.util.DateUtil;
import gov.samhsa.ocp.ocpfis.util.FhirDtoUtil;
import gov.samhsa.ocp.ocpfis.util.PaginationUtil;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.dstu3.model.Annotation;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.EpisodeOfCare;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.ResourceType;
import org.hl7.fhir.dstu3.model.Task;
import org.hl7.fhir.dstu3.model.codesystems.EpisodeofcareType;
import org.hl7.fhir.dstu3.model.codesystems.TaskStatus;
import org.hl7.fhir.exceptions.FHIRException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static gov.samhsa.ocp.ocpfis.util.FhirDtoUtil.mapReferenceDtoToReference;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final ModelMapper modelMapper;

    private final IGenericClient fhirClient;

    private final FhirValidator fhirValidator;

    private final LookUpService lookUpService;

    private final FisProperties fisProperties;

    private final EpisodeOfCareService episodeOfCareService;

    @Autowired
    public TaskServiceImpl(ModelMapper modelMapper,
                           IGenericClient fhirClient,
                           FhirValidator fhirValidator,
                           LookUpService lookUpService,
                           FisProperties fisProperties,
                           EpisodeOfCareService episodeOfCareService) {
        this.modelMapper = modelMapper;
        this.fhirClient = fhirClient;
        this.fhirValidator = fhirValidator;
        this.lookUpService = lookUpService;
        this.fisProperties = fisProperties;
        this.episodeOfCareService = episodeOfCareService;
    }

    @Override
    public PageDto<TaskDto> getTasks(Optional<List<String>> statusList, String searchKey, String searchValue, Optional<Integer> pageNumber, Optional<Integer> pageSize) {
        int numberOfTasksPerPage = PaginationUtil.getValidPageSize(fisProperties, pageSize, ResourceType.Task.name());
        IQuery iQuery = fhirClient.search().forResource(Task.class);

        //Check for Patient
        if (searchKey.equalsIgnoreCase("patientId"))
            iQuery.where(new ReferenceClientParam("patient").hasId("Patient/" + searchValue));

        //Check for Organization
        if (searchKey.equalsIgnoreCase("organizationId"))
            iQuery.where(new ReferenceClientParam("organization").hasId("Organization/" + searchValue));

        //Check for Task
        if (searchKey.equalsIgnoreCase("taskId"))
            iQuery.where(new TokenClientParam("_id").exactly().code(searchValue));

        //Check for Status
        if (statusList.isPresent() && !statusList.get().isEmpty()) {
            iQuery.where(new TokenClientParam("status").exactly().codes(statusList.get()));
        }

        Bundle firstPageTaskBundle;
        Bundle otherPageTaskBundle;
        boolean firstPage = true;

        firstPageTaskBundle = (Bundle) iQuery
                .count(numberOfTasksPerPage)
                .returnBundle(Bundle.class)
                .execute();

        if (firstPageTaskBundle == null || firstPageTaskBundle.getEntry().isEmpty()) {
            throw new ResourceNotFoundException("No Tasks were found in the FHIR server.");
        }

        otherPageTaskBundle = firstPageTaskBundle;

        if (pageNumber.isPresent() && pageNumber.get() > 1 && otherPageTaskBundle.getLink(Bundle.LINK_NEXT) != null) {
            firstPage = false;
            otherPageTaskBundle = PaginationUtil.getSearchBundleAfterFirstPage(fhirClient, fisProperties, firstPageTaskBundle, pageNumber.get(), numberOfTasksPerPage);
        }

        List<Bundle.BundleEntryComponent> retrievedTasks = otherPageTaskBundle.getEntry();


        List<TaskDto> taskDtos = retrievedTasks.stream().filter(retrivedBundle -> retrivedBundle.getResource().getResourceType().equals(ResourceType.Task)).map(retrievedTask -> {

            Task task = (Task) retrievedTask.getResource();

            TaskDto taskDto = new TaskDto();
            ValueSetDto performerTypeDto = new ValueSetDto();

            taskDto.setLogicalId(task.getIdElement().getIdPart());
            taskDto.setDescription(task.getDescription());
            if (task.getNote() != null && (task.getNote().size() > 0))
                taskDto.setNote(task.getNote().get(0).getText());

            if (task.getStatus() != null) {

                taskDto.setStatus(ValueSetDto.builder()
                        .code((task.getStatus().toCode() != null && !task.getStatus().toCode().isEmpty()) ? task.getStatus().toCode() : null)
                        .display((task.getStatus().getDisplay() != null && !task.getStatus().getDisplay().isEmpty()) ? task.getStatus().getDisplay() : null)
                        .build());
            }

            if (task.getIntent() != null) {

                taskDto.setIntent(ValueSetDto.builder()
                        .code((task.getIntent().toCode() != null && !task.getIntent().toCode().isEmpty()) ? task.getIntent().toCode() : null)
                        .display((task.getIntent().getDisplay() != null && !task.getIntent().getDisplay().isEmpty()) ? task.getIntent().getDisplay() : null)
                        .build());
            }

            if (task.getPriority() != null) {
                taskDto.setPriority(ValueSetDto.builder()
                        .code((task.getPriority().toCode() != null && !task.getPriority().toCode().isEmpty()) ? task.getPriority().toCode() : null)
                        .display((task.getPriority().getDisplay() != null && !task.getPriority().getDisplay().isEmpty()) ? task.getPriority().getDisplay() : null)
                        .build());
            }

            if (task.getPerformerType() != null) {
                task.getPerformerType().stream().findFirst().ifPresent(performerType -> performerType.getCoding().stream().findFirst().ifPresent(coding -> {
                    performerTypeDto.setCode((coding.getCode() != null && !coding.getCode().isEmpty()) ? coding.getCode() : null);
                    performerTypeDto.setDisplay((FhirDtoUtil.getDisplayForCode(coding.getCode(), Optional.ofNullable(lookUpService.getTaskPerformerType()))).orElse(null));
                }));

                taskDto.setPerformerType(performerTypeDto);
            }

            if (task.hasPartOf()) {
                taskDto.setPartOf(ReferenceDto.builder()
                        .reference((task.getPartOf().get(0).getReference() != null && !task.getPartOf().get(0).getReference().isEmpty()) ? task.getPartOf().get(0).getReference() : null)
                        .display((task.getPartOf().get(0).getDisplay() != null && !task.getPartOf().get(0).getDisplay().isEmpty()) ? task.getPartOf().get(0).getDisplay() : null)
                        .build());
            }

            if (task.hasFor()) {
                taskDto.setBeneficiary(ReferenceDto.builder()
                        .reference((task.getFor().getReference() != null && !task.getFor().getReference().isEmpty()) ? task.getFor().getReference() : null)
                        .display((task.getFor().getDisplay() != null && !task.getFor().getDisplay().isEmpty()) ? task.getFor().getDisplay() : null)
                        .build());
            }

            if (task.hasRequester()) {
                if (task.getRequester().hasOnBehalfOf())
                    taskDto.setOnBehalfOf(ReferenceDto.builder()
                            .reference((task.getRequester().getOnBehalfOf().getReference() != null && !task.getRequester().getOnBehalfOf().getReference().isEmpty()) ? task.getRequester().getOnBehalfOf().getReference() : null)
                            .display((task.getRequester().getOnBehalfOf().getDisplay() != null && !task.getRequester().getOnBehalfOf().getDisplay().isEmpty()) ? task.getRequester().getOnBehalfOf().getDisplay() : null)
                            .build());
            }

            if (task.hasRequester()) {
                if (task.getRequester().hasAgent())
                    taskDto.setAgent(ReferenceDto.builder()
                            .reference((task.getRequester().getAgent().getReference() != null && !task.getRequester().getAgent().getReference().isEmpty()) ? task.getRequester().getOnBehalfOf().getReference() : null)
                            .display((task.getRequester().getAgent().getDisplay() != null && !task.getRequester().getAgent().getDisplay().isEmpty()) ? task.getRequester().getOnBehalfOf().getDisplay() : null)
                            .build());
            }

            if (task.hasOwner()) {
                taskDto.setOwner(ReferenceDto.builder()
                        .reference((task.getOwner().getReference() != null && !task.getOwner().getReference().isEmpty()) ? task.getOwner().getReference() : null)
                        .display((task.getOwner().getDisplay() != null && !task.getOwner().getDisplay().isEmpty()) ? task.getOwner().getDisplay() : null)
                        .build());
            }

            if (task.hasDefinition()) {
                try {
                    taskDto.setDefinition(ReferenceDto.builder()
                            .reference((task.hasDefinitionReference()) ? task.getDefinitionReference().getReference() : null)
                            .display((task.hasDefinitionReference()) ? task.getDefinitionReference().getDisplay() : null)
                            .build());
                } catch (FHIRException e) {
                    log.error("FHIR Exception when setting task definition", e);
                }
            }

            //TODO: redo context field

            if (task.hasLastModified()) {
                taskDto.setLastModified(DateUtil.convertDateToLocalDate(task.getLastModified()));
            }

            if (task.hasAuthoredOn()) {
                taskDto.setAuthoredOn(DateUtil.convertDateToLocalDate(task.getAuthoredOn()));
            }

            if (task.getExecutionPeriod() != null && !task.getExecutionPeriod().isEmpty()) {
                PeriodDto periodDto = new PeriodDto();
                taskDto.setExecutionPeriod(periodDto);
                taskDto.getExecutionPeriod().setStart((task.getExecutionPeriod().hasStart()) ? DateUtil.convertDateToLocalDate(task.getExecutionPeriod().getStart()) : null);
                taskDto.getExecutionPeriod().setEnd((task.getExecutionPeriod().hasEnd()) ? DateUtil.convertDateToLocalDate(task.getExecutionPeriod().getEnd()) : null);
            }

            return taskDto;

        }).collect(toList());

        double totalPages = Math.ceil((double) otherPageTaskBundle.getTotal() / numberOfTasksPerPage);
        int currentPage = firstPage ? 1 : pageNumber.get();

        return new PageDto<>(taskDtos, numberOfTasksPerPage, totalPages, currentPage, taskDtos.size(), otherPageTaskBundle.getTotal());
    }


    @Override
    public void createTask(TaskDto taskDto) {
        if (!isDuplicate(taskDto)) {
            Task task = setTaskDtoToTask(taskDto);

            //Checking activity definition for enrollment and creating context with Episode of care
            if (taskDto.getDefinition().getDisplay().equalsIgnoreCase("Enrollment")) {

                Optional<EpisodeOfCareDto> episodeOfCare = retrieveEpisodeOfCare(taskDto);

                Reference contextReference = new Reference();

                if (episodeOfCare.isPresent()) {
                    EpisodeOfCareDto dto = episodeOfCare.get();
                    contextReference.setReference(ResourceType.EpisodeOfCare + "/" + dto.getId());
                } else {
                    EpisodeOfCare newEpisodeOfCare = createEpisodeOfCare(taskDto);
                    MethodOutcome methodOutcome = fhirClient.create().resource(newEpisodeOfCare).execute();
                    contextReference.setReference(ResourceType.EpisodeOfCare + "/" + methodOutcome.getId().getIdPart());
                }

                contextReference.setDisplay(createDisplayForEpisodeOfCare(taskDto));
                task.setContext(contextReference);
            }

            //Set authoredOn
            task.setAuthoredOn(java.sql.Date.valueOf(LocalDate.now()));

            fhirClient.create().resource(task).execute();
        } else {
            throw new DuplicateResourceFoundException("Duplicate task is already present.");
        }
    }

    @Override
    public void updateTask(String taskId, TaskDto taskDto) {
        Task task = setTaskDtoToTask(taskDto);
        task.setId(taskId);

        Bundle taskBundle = fhirClient.search().forResource(Task.class)
                .where(new TokenClientParam("_id").exactly().code(taskId))
                .returnBundle(Bundle.class)
                .execute();

        Task existingTask = (Task) taskBundle.getEntry().stream().findFirst().get().getResource();

        if (taskDto.getDefinition().getDisplay().equalsIgnoreCase("Enrollment")) {

            if (!existingTask.hasContext()) {
                EpisodeOfCare episodeOfCare = createEpisodeOfCare(taskDto);
                MethodOutcome methodOutcome = fhirClient.create().resource(episodeOfCare).execute();
                Reference contextReference = new Reference();
                task.setContext(contextReference.setReference("EpisodeOfCare/" + methodOutcome.getId().getIdPart()));
            } else {
                EpisodeOfCare episodeOfCare = createEpisodeOfCare(taskDto);
                episodeOfCare.setId(existingTask.getContext().getReference().split("/")[1]);
                MethodOutcome methodOutcome = fhirClient.update().resource(episodeOfCare).execute();
                Reference contextReference = new Reference();
                task.setContext(contextReference.setReference("EpisodeOfCare/" + methodOutcome.getId().getIdPart()));
            }
        }

        task.setAuthoredOn(existingTask.getAuthoredOn());
        fhirClient.update().resource(task).execute();
    }

    @Override
    public void deactivateTask(String taskId) {
        Task task = fhirClient.read().resource(Task.class).withId(taskId.trim()).execute();
        task.setStatus(Task.TaskStatus.CANCELLED);
        fhirClient.update().resource(task).execute();
    }

    @Override
    public TaskDto getTaskById(String taskId) {
        Bundle taskBundle = fhirClient.search().forResource(Task.class)
                .where(new TokenClientParam("_id").exactly().code(taskId))
                .include(Task.INCLUDE_CONTEXT)
                .returnBundle(Bundle.class)
                .execute();

        TaskDto taskDto = new TaskDto();

        taskBundle.getEntry().stream()
                .filter(taskResource -> taskResource.getResource().getResourceType().equals(ResourceType.Task))
                .findFirst().ifPresent(taskPresent -> {
            Task task = (Task) taskPresent.getResource();
            //Setting definition
            taskDto.setLogicalId(task.getIdElement().getIdPart());
            try {
                taskDto.setDefinition(FhirDtoUtil.convertReferenceToReferenceDto(task.getDefinitionReference()));
            } catch (FHIRException e) {
                e.printStackTrace();
            }

            if (task.hasPartOf()) {
                taskDto.setPartOf(FhirDtoUtil.convertReferenceToReferenceDto(task.getPartOf().stream().findFirst().get()));
            }

            //Setting Status, Intent, Priority
            taskDto.setStatus(FhirDtoUtil.convertCodeToValueSetDto(task.getStatus().toCode(), lookUpService.getTaskStatus()));
            taskDto.setIntent(FhirDtoUtil.convertCodeToValueSetDto(task.getIntent().toCode(), lookUpService.getRequestIntent()));
            taskDto.setPriority(FhirDtoUtil.convertCodeToValueSetDto(task.getPriority().toCode(), lookUpService.getRequestPriority()));

            if (task.hasDescription()) {
                taskDto.setDescription(task.getDescription());
            }

            taskDto.setBeneficiary(FhirDtoUtil.convertReferenceToReferenceDto(task.getFor()));

            taskDto.setAgent(FhirDtoUtil.convertReferenceToReferenceDto(task.getRequester().getAgent()));

            taskDto.setOnBehalfOf(FhirDtoUtil.convertReferenceToReferenceDto(task.getRequester().getOnBehalfOf()));

            //Set Performer Type
            if (task.hasPerformerType()) {
                task.getPerformerType().stream().findFirst().ifPresent(performerType -> performerType.getCoding().stream().findFirst().ifPresent(coding -> {
                    taskDto.setPerformerType(FhirDtoUtil.convertCodeToValueSetDto(coding.getCode(), lookUpService.getTaskPerformerType()));
                }));
            }

            taskDto.setOwner(FhirDtoUtil.convertReferenceToReferenceDto(task.getOwner()));

            //Set Note
            task.getNote().stream().findFirst().ifPresent(note -> taskDto.setNote(note.getText()));

            if (task.hasLastModified()) {
                taskDto.setLastModified(DateUtil.convertDateToLocalDate(task.getLastModified()));
            }

            if (task.hasAuthoredOn()) {
                taskDto.setAuthoredOn(DateUtil.convertDateToLocalDate(task.getAuthoredOn()));
            }

            if (task.getExecutionPeriod() != null && !task.getExecutionPeriod().isEmpty()) {
                PeriodDto periodDto = new PeriodDto();
                periodDto.setStart((task.getExecutionPeriod().hasStart()) ? DateUtil.convertDateToLocalDate(task.getExecutionPeriod().getStart()) : null);
                periodDto.setEnd((task.getExecutionPeriod().hasEnd()) ? DateUtil.convertDateToLocalDate(task.getExecutionPeriod().getEnd()) : null);
                taskDto.setExecutionPeriod(periodDto);
            }

            //Setting context
            if (task.hasContext()) {
                taskBundle.getEntry().stream().filter(bundle -> bundle.getResource().getResourceType().equals(ResourceType.EpisodeOfCare))
                        .findFirst().ifPresent(episodeOfCareBundle -> {
                    EpisodeOfCare episodeOfCare = (EpisodeOfCare) episodeOfCareBundle.getResource();
                    ContextDto contextDto = new ContextDto();
                    contextDto.setLogicalId(episodeOfCare.getIdElement().getIdPart());
                    contextDto.setStatus(episodeOfCare.getStatus().toCode());

                    if(episodeOfCare.hasType()) {
                        episodeOfCare.getType().stream().findFirst().ifPresent(eocType->{
                            ValueSetDto valueSetDto=new ValueSetDto();
                            eocType.getCoding().stream().findFirst().ifPresent(type->{
                              valueSetDto.setCode((type.hasCode())?type.getCode():null);
                               valueSetDto.setSystem((type.hasSystem())?type.getSystem():null);
                                valueSetDto.setDisplay((type.hasDisplay())?type.getDisplay():null);
                            });
                            contextDto.setType(valueSetDto);
                        });
                    }
                    if (episodeOfCare.hasPatient())
                        contextDto.setPatient(FhirDtoUtil.convertReferenceToReferenceDto(episodeOfCare.getPatient()));
                    if (episodeOfCare.hasManagingOrganization())
                        contextDto.setManagingOrganization(FhirDtoUtil.convertReferenceToReferenceDto(episodeOfCare.getManagingOrganization()));
                    if (episodeOfCare.hasReferralRequest())
                        contextDto.setReferralRequest(FhirDtoUtil.convertReferenceToReferenceDto((Reference) episodeOfCare.getReferralRequest()));
                    if (episodeOfCare.hasCareManager())
                        contextDto.setCareManager(FhirDtoUtil.convertReferenceToReferenceDto(episodeOfCare.getCareManager()));

                    if(episodeOfCare.hasPeriod()) {
                        PeriodDto periodDto=new PeriodDto();
                        periodDto.setStart((episodeOfCare.getPeriod().hasStart()) ? DateUtil.convertDateToLocalDate(task.getExecutionPeriod().getStart()) : null);
                        periodDto.setEnd((episodeOfCare.getPeriod().hasEnd())? DateUtil.convertDateToLocalDate(task.getExecutionPeriod().getEnd()):null);
                        contextDto.setPeriod(periodDto);
                    }

                    //TODO: revisit the context logic
                    //taskDto.setContext(contextDto);
                });
            }
        });

        return taskDto;

    }

    public List<ReferenceDto> getRelatedTasks(String patient) {
        List<ReferenceDto> tasks = new ArrayList<>();

        Bundle bundle = fhirClient.search().forResource(Task.class)
                .where(new ReferenceClientParam("patient").hasId(ResourceType.Patient + "/" + patient))
                .returnBundle(Bundle.class).execute();

        if (bundle != null) {
            List<Bundle.BundleEntryComponent> taskComponents = bundle.getEntry();

            if (taskComponents != null) {
                tasks = taskComponents.stream()
                        .map(it -> (Task) it.getResource())
                        .map(it -> FhirDtoUtil.mapTaskToReferenceDto(it))
                        .collect(toList());
            }
        }

        return tasks;
    }

    private boolean isDuplicate(TaskDto taskDto) {
        Bundle taskForPatientbundle = fhirClient.search().forResource(Task.class)
                .where(new ReferenceClientParam("patient").hasId(taskDto.getBeneficiary().getReference()))
                .returnBundle(Bundle.class)
                .execute();

        List<Bundle.BundleEntryComponent> duplicateCheckList = new ArrayList<>();
        if (!taskForPatientbundle.isEmpty()) {
            duplicateCheckList = taskForPatientbundle.getEntry().stream().filter(taskResource -> {
                Task task = (Task) taskResource.getResource();
                try {
                    return task.getDefinitionReference().getReference().equalsIgnoreCase(taskDto.getDefinition().getReference());

                } catch (FHIRException e) {
                    throw new ResourceNotFoundException("No definition reference found in the Server");
                }
            }).collect(Collectors.toList());
        }
        return !duplicateCheckList.isEmpty();
    }

    private Task setTaskDtoToTask(TaskDto taskDto) {
        Task task = new Task();
        task.setDefinition(FhirDtoUtil.mapReferenceDtoToReference(taskDto.getDefinition()));

        if (taskDto.getPartOf() != null) {
            List<Reference> partOfReferences = new ArrayList<>();
            partOfReferences.add(mapReferenceDtoToReference(taskDto.getPartOf()));
            task.setPartOf(partOfReferences);
        }

        task.setStatus(Task.TaskStatus.valueOf(taskDto.getStatus().getCode().toUpperCase()));
        task.setIntent(Task.TaskIntent.valueOf(taskDto.getIntent().getCode().toUpperCase()));
        task.setPriority(Task.TaskPriority.valueOf(taskDto.getPriority().getCode().toUpperCase()));

        if (taskDto.getDescription() != null && !taskDto.getDescription().isEmpty()) {
            task.setDescription(taskDto.getDescription());
        }

        task.setFor(mapReferenceDtoToReference(taskDto.getBeneficiary()));

        //Set execution Period
        if(taskDto.getExecutionPeriod() !=null){
            if(taskDto.getExecutionPeriod().getStart() !=null)
                task.getExecutionPeriod().setStart(java.sql.Date.valueOf(taskDto.getExecutionPeriod().getStart()));
        } else if (taskDto.getStatus().getCode().equalsIgnoreCase(TaskStatus.INPROGRESS.toCode()))
            task.getExecutionPeriod().setStart(java.sql.Date.valueOf(LocalDate.now()));

        if(taskDto.getExecutionPeriod() !=null){
            if(taskDto.getExecutionPeriod().getEnd() !=null)
                task.getExecutionPeriod().setEnd(java.sql.Date.valueOf(taskDto.getExecutionPeriod().getEnd()));
        } else if (taskDto.getStatus().getCode().equalsIgnoreCase(TaskStatus.COMPLETED.toCode()))
            task.getExecutionPeriod().setEnd(java.sql.Date.valueOf(LocalDate.now()));

        //Set agent
        task.getRequester().setAgent(mapReferenceDtoToReference(taskDto.getAgent()));

        //Set on Behalf of
        if (taskDto.getOnBehalfOf() != null) {
            task.getRequester().setOnBehalfOf(mapReferenceDtoToReference(taskDto.getOnBehalfOf()));
        }

        //Set PerformerType
        if (taskDto.getPerformerType() != null) {
            List<CodeableConcept> codeableConcepts = new ArrayList<>();
            CodeableConcept codeableConcept = new CodeableConcept();
            codeableConcept.addCoding().setCode(taskDto.getPerformerType().getCode())
                                        .setDisplay(taskDto.getPerformerType().getDisplay())
                                        .setSystem(taskDto.getPerformerType().getSystem());
            codeableConcepts.add(codeableConcept);
            task.setPerformerType(codeableConcepts);
        }

        //Set last Modified
        task.setLastModified(java.sql.Date.valueOf(LocalDate.now()));

        task.setOwner(mapReferenceDtoToReference(taskDto.getOwner()));

        Annotation annotation = new Annotation();
        annotation.setText(taskDto.getNote());
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        task.setNote(annotations);

        return task;
    }

    private EpisodeOfCare createEpisodeOfCare(TaskDto taskDto) {
        EpisodeOfCare episodeOfCare = new EpisodeOfCare();
        episodeOfCare.setStatus(EpisodeOfCare.EpisodeOfCareStatus.ACTIVE);

        //Setting Episode of care type tp HACC
        CodeableConcept codeableConcept = new CodeableConcept();
        codeableConcept.addCoding().setSystem(EpisodeofcareType.HACC.getSystem())
                                    .setDisplay(EpisodeofcareType.HACC.getDisplay())
                                    .setCode(EpisodeofcareType.HACC.toCode());
        List<CodeableConcept> codeableConcepts = new ArrayList<>();
        codeableConcepts.add(codeableConcept);

        episodeOfCare.setType(codeableConcepts);

        //patient
        episodeOfCare.setPatient(mapReferenceDtoToReference(taskDto.getBeneficiary()));

        //managing organization
        episodeOfCare.setManagingOrganization(mapReferenceDtoToReference(taskDto.getOrganization()));

        //start date
        episodeOfCare.getPeriod().setStart(java.sql.Date.valueOf(taskDto.getExecutionPeriod().getStart()));

        //careManager
        episodeOfCare.setCareManager(mapReferenceDtoToReference(taskDto.getAgent()));

        return episodeOfCare;
    }

    private Optional<EpisodeOfCareDto> retrieveEpisodeOfCare(TaskDto taskDto) {
        String patient = mapReferenceDtoToReference(taskDto.getBeneficiary()).getReference();

        List<EpisodeOfCareDto> episodeOfCareDtos = episodeOfCareService.getEpisodeOfCares(patient, Optional.of(taskDto.getStatus().getCode()));

        return episodeOfCareDtos.stream().findFirst();
    }

    private static String createDisplayForEpisodeOfCare(TaskDto dto) {
        String status = dto.getDefinition() != null ? dto.getDefinition().getDisplay() : "NA";
        String date = dto.getExecutionPeriod() != null ? DateUtil.convertLocalDateToString(dto.getExecutionPeriod().getStart()) : "NA";
        String agent = dto.getAgent() != null ? dto.getAgent().getDisplay() : "NA";

        return new StringJoiner("-").add(status).add(date).add(agent).toString();
    }

}
