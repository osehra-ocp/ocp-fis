package gov.samhsa.ocp.ocpfis.service.mapping;

import gov.samhsa.ocp.ocpfis.service.dto.TelecomDto;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.modelmapper.AbstractConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TelecomListToTelecomDtoListConverter extends AbstractConverter<List<ContactPoint>, List<TelecomDto>> {

    @Override
    protected List<TelecomDto> convert(List<ContactPoint> source) {
        List<TelecomDto> telecomDtoList = new ArrayList<>();

        if (source != null && source.size() > 0) {

            for (ContactPoint tempTelecom : source) {
                TelecomDto tempTelecomDto = new TelecomDto();
                tempTelecomDto.setValue(tempTelecom.getValue());
                if (tempTelecom.getSystem() != null)
                    tempTelecomDto.setSystem(tempTelecom.getSystem().toString());
                if (tempTelecom.getUse() != null)
                    tempTelecomDto.setUse(tempTelecom.getUse().toString());
                telecomDtoList.add(tempTelecomDto);
            }
        }
        return telecomDtoList;
    }
}