package gov.samhsa.ocp.ocpfis.web;

import gov.samhsa.ocp.ocpfis.service.LookUpService;
import gov.samhsa.ocp.ocpfis.service.dto.IdentifierSystemDto;
import gov.samhsa.ocp.ocpfis.service.dto.OrganizationStatusDto;
import gov.samhsa.ocp.ocpfis.service.dto.ValueSetDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/lookups")
public class LookUpController {

    private final LookUpService lookUpService;

    public LookUpController(LookUpService lookUpService) {
        this.lookUpService = lookUpService;
    }

    @GetMapping("/usps-states")
    public List<ValueSetDto> getUspsStates() {
        return lookUpService.getUspsStates();
    }

    /**
     * Determine identifier to use for a specific purpose
     * Eg: PRN , EN
     * @return
     */
    @GetMapping("/identifier-types")
    public List<ValueSetDto> getIdentifierTypes(@RequestParam(value = "resourceType") Optional<String> resourceType) {
        return lookUpService.getIdentifierTypes(resourceType);
    }


    @GetMapping("/identifier-systems")
    public List<IdentifierSystemDto> getIdentifierSystems(@RequestParam(value = "identifierTypeList") Optional<List<String>> identifierTypeList){
        return lookUpService.getIdentifierSystems(identifierTypeList);
    }

    /**
     * Identifies the purpose for this identifier, if known
     * Eg: Usual, Official, Temp
     * @return
     */
    @GetMapping("/identifier-uses")
    public List<ValueSetDto> getIdentifierUses() {
        return lookUpService.getIdentifierUses();
    }

    //LOCATION START

    /**
     * Indicates whether a resource instance represents a specific location or a class of locations
     * Eg: INSTANCE, KIND, NULL
     * @return
     */
    @GetMapping("/location-modes")
    public List<ValueSetDto> getLocationModes() {
        return lookUpService.getLocationModes();
    }

    /**
     * general availability of the resource
     * Eg: ACTIVE, SUSPENDED, INACTIVE, NULL
     * @return
     */
    @GetMapping("/location-statuses")
    public List<ValueSetDto> getLocationStatuses() {
        return lookUpService.getLocationStatuses();
    }

    /**
     * Physical form of the location
     * e.g. building, room, vehicle, road.
     */
    @GetMapping("/location-physical-types")
    public List<ValueSetDto> getLocationPhysicalTypes() {
        return lookUpService.getLocationPhysicalTypes();
    }


    //LOCATION END

    //ADDRESS and TELECOM START

    /**
     * The type of an address (physical / postal)
     * Eg: POSTAL, PHYSICAL, POSTAL & PHYSICAL, NULL
     * @return
     */
    @GetMapping("/address-types")
    public List<ValueSetDto> getAddressTypes() {
        return lookUpService.getAddressTypes();
    }

    /**
     * The use of an address
     * Eg: HOME, WORK, TEMP, OLD, NULL
     * @return
     */
    @GetMapping("/address-uses")
    public List<ValueSetDto> getAddressUses() {
        return lookUpService.getAddressUses();
    }

    /**
     * Identifies the purpose for the contact point
     * Eg: HOME, WORK, TEMP, OLD, MOBILE, NULL
     * @return
     */
    @GetMapping("/telecom-uses")
    public List<ValueSetDto> getTelecomUses() {
        return lookUpService.getTelecomUses();
    }

    /**
     * Telecommunications form for contact point - what communications system is required to make use of the contact.
     * Eg: PHONE, FAX, EMAIL, PAGER, URL, SMS, OTHER, NULL
     * @return
     */
    @GetMapping("/telecom-systems")
    public List<ValueSetDto> getTelecomSystems() {
        return lookUpService.getTelecomSystems();
    }

    //ADDRESS and TELECOM END


    @GetMapping("/organization-statuses")
    public List<OrganizationStatusDto> getOrganizationStatuses() {
        return lookUpService.getOrganizationStatuses();
    }

}
