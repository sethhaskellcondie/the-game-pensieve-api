package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.auth.AccessService;
import com.sethhaskellcondie.thegamepensieveapi.domain.auth.Capability;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionForbidden;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionPaymentRequired;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterRequestDto;

import java.util.List;

/**
 * Base gateway shared by every catalog entity. This is the single chokepoint for the role-based access model:
 * each method is a distinct semantic verb, so the capability gates live here once and cover all entities.
 * <ul>
 *   <li>{@code getById} — ungated single read (Row-Level Security already scopes the row to the caller).</li>
 *   <li>{@code getWithFilters} — an unfiltered list is always allowed, but a filtered query requires the
 *       {@code FILTER} capability (402 otherwise). RLS keeps a GUEST on the showcase.</li>
 *   <li>{@code createNew}/{@code updateExisting}/{@code deleteById} — require the {@code WRITE} capability (403
 *       otherwise). An anonymous GUEST is already blocked at Spring Security in the secured build, so this
 *       realistically fires for LAPSED.</li>
 * </ul>
 */
public abstract class EntityGatewayAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityGateway<T, RequestDto, ResponseDto> {
    protected final EntityService<T, RequestDto, ResponseDto> service;
    protected final AccessService access;

    public EntityGatewayAbstract(EntityService<T, RequestDto, ResponseDto> service, AccessService access) {
        this.service = service;
        this.access = access;
    }

    @Override
    public List<ResponseDto> getWithFilters(List<FilterRequestDto> filters) {
        if (filters != null && !filters.isEmpty() && !access.can(Capability.FILTER)) {
            throw new ExceptionPaymentRequired(
                    "Filtered searches require an active subscription. List your data without filters, or renew to filter.");
        }
        List<T> t = service.getWithFilters(filters);
        return t.stream().map(e -> e.convertToResponseDto()).toList();
    }

    @Override
    public ResponseDto getById(int id) {
        return service.getById(id).convertToResponseDto();
    }

    @Override
    public ResponseDto createNew(RequestDto requestDto) {
        requireWrite();
        return service.createNew(requestDto).convertToResponseDto();
    }

    @Override
    public ResponseDto updateExisting(int id, RequestDto requestDto) {
        requireWrite();
        return service.updateExisting(id, requestDto).convertToResponseDto();
    }

    @Override
    public void deleteById(int id) {
        requireWrite();
        service.deleteById(id);
    }

    protected void requireWrite() {
        if (!access.can(Capability.WRITE)) {
            throw new ExceptionForbidden("An active subscription is required to create, update, or delete data.");
        }
    }
}
