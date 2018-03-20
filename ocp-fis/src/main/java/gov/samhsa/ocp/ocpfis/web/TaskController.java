package gov.samhsa.ocp.ocpfis.web;

import gov.samhsa.ocp.ocpfis.service.TaskService;
import gov.samhsa.ocp.ocpfis.service.dto.PageDto;
import gov.samhsa.ocp.ocpfis.service.dto.ReferenceDto;
import gov.samhsa.ocp.ocpfis.service.dto.TaskDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
public class TaskController {

    @Autowired
    private TaskService taskService;

    @GetMapping("/tasks/search")
    public PageDto<TaskDto> getTasks(@RequestParam Optional<List<String>> statusList,
                                     @RequestParam(value = "searchType") String searchKey,
                                     @RequestParam(value = "searchValue") String searchValue,
                                     @RequestParam(value = "pageNumber") Optional<Integer> pageNumber,
                                     @RequestParam(value = "pageSize") Optional<Integer> pageSize) {
        return taskService.getTasks(statusList, searchKey, searchValue, pageNumber, pageSize);
    }


    @GetMapping("/tasks/subtasks")
    public List<TaskDto> getSubTasks(@RequestParam(value = "practitionerId") Optional<String> practitionerId,
                                     @RequestParam(value = "patientId") Optional<String> patientId,
                                     @RequestParam(value = "definition") Optional<String> definition) {
        return taskService.getSubTasks(practitionerId, patientId, definition);
    }


    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public void createTask(@Valid @RequestBody TaskDto taskDto) {
        taskService.createTask(taskDto);
    }

    @PutMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.OK)
    public void updateTask(@PathVariable String taskId, @Valid @RequestBody TaskDto taskDto) {
        taskService.updateTask(taskId, taskDto);
    }

    @PutMapping("/tasks/{taskId}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public void deactivateTask(@PathVariable String taskId) {
        taskService.deactivateTask(taskId);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskDto getTaskById(@PathVariable String taskId) {
        return taskService.getTaskById(taskId);
    }

    @GetMapping("/tasks/task-references")
    public List<ReferenceDto> getRelatedTasks(@RequestParam String patient, @RequestParam Optional<String> definition) {
        return taskService.getRelatedTasks(patient, definition);
    }

    @GetMapping("/tasks")
    public List<TaskDto> getUpcomingTasks(@RequestParam(value = "practitioner") String practitioner) {
        return taskService.getUpcomingTasks(practitioner);
    }

}
