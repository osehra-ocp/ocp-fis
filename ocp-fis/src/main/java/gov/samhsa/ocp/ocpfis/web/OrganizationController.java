package gov.samhsa.ocp.ocpfis.web;

import gov.samhsa.ocp.ocpfis.service.OrganizationService;
import gov.samhsa.ocp.ocpfis.service.dto.OrganizationDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;



@RestController
@RequestMapping("/organizations")

public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationDto> getAllOrganization() {
        return organizationService.getAllOrganizations();
    }

    @GetMapping("/search")
    public List<OrganizationDto> searchOrganization(@Valid @RequestParam String name) {
        return organizationService.searchOrganization(name);
    }

}