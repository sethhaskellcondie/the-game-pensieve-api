package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityGatewayAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityService;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionFailedDbValidation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToyGateway extends EntityGatewayAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityGateway<Toy, ToyRequestDto, ToyResponseDto> {
    public ToyGateway(EntityService<Toy, ToyRequestDto, ToyResponseDto> service) {
        super(service);
    }

    //This method is ONLY here to seed data in the HeartbeatController TODO update how we seed data and remove this method
    @Deprecated
    public ToyResponseDto createNew(String name, String set, List<CustomFieldValue> customFieldValues) throws ExceptionFailedDbValidation {
        return this.createNew(new ToyRequestDto(name, set, customFieldValues));
    }
}
