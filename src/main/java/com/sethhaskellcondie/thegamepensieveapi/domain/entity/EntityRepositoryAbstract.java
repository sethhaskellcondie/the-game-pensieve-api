package com.sethhaskellcondie.thegamepensieveapi.domain.entity;

import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.customfield.CustomFieldValueRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.FilterService;
import com.sethhaskellcondie.thegamepensieveapi.domain.ErrorLogs;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Types;
import java.util.List;

/**
 * The goal of creating an abstract of the repository is to encapsulate the access of the customFieldValueRepository
 * That should be as automatic as possible, when adding new entities developers should be aware of it but not have to
 * implement it every time. This will also make the exceptions and logging uniform across all EntityRepositories.
 */
public abstract class EntityRepositoryAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityRepository<T, RequestDto, ResponseDto> {

    protected final JdbcTemplate jdbcTemplate;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final CustomFieldRepository customFieldRepository;
    private final String baseQuery;
    private final String baseQueryJoinCustomFieldValues;
    private final String baseQueryWhereDeletedAtIsNotNull;
    private final String baseQueryIncludeDeleted;
    private final String entityKey;
    private final RowMapper<T> rowMapper;
    private final Logger logger = LoggerFactory.getLogger(EntityRepositoryAbstract.class);


    protected EntityRepositoryAbstract(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldValueRepository = new CustomFieldValueRepository(jdbcTemplate);
        this.customFieldRepository = new CustomFieldRepository(jdbcTemplate);
        this.baseQuery = this.getBaseQueryExcludeDeleted();
        this.baseQueryJoinCustomFieldValues = this.getBaseQueryJoinCustomFieldValues();
        this.baseQueryWhereDeletedAtIsNotNull = this.getBaseQueryWhereIsDeleted();
        this.baseQueryIncludeDeleted = this.getBaseQuery();
        this.entityKey = this.getEntityKey();
        this.rowMapper = this.getRowMapper();
    }

    //DO NOT end the base queries with a ';' they will be appended
    protected abstract String getBaseQuery();
    protected String getBaseQueryExcludeDeleted() {
        return getBaseQuery() + " AND deleted_at IS NULL ";
    }

    protected String getBaseQueryWhereIsDeleted() {
        return getBaseQuery() + " AND deleted_at IS NOT NULL ";
    }
    protected abstract String getBaseQueryJoinCustomFieldValues();
    protected abstract String getEntityKey();
    protected abstract RowMapper<T> getRowMapper();

    //This method will be commonly used to validate objects before they are inserted or updated,
    //performing any validation that is not enforced by the database schema
    protected abstract void insertValidation(T entity);
    protected abstract void updateValidation(T entity);
    protected abstract Integer insertImplementation(T entity);
    protected abstract void updateImplementation(T entity);

    @Override
    public T insert(T entity) {
        insertValidation(entity);

        Integer id = insertImplementation(entity);

        final T savedEntity;
        try {
            savedEntity = getById(id);
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            //we shouldn't ever reach this block because the database is managing the ID's
            logger.error(ErrorLogs.InsertThenRetrieveError(entityKey, id));
            throw new ExceptionInternalCatastrophe(entityKey, id);
        }
        savedEntity.setCustomFieldValues(customFieldValueRepository.upsertValues(entity.getCustomFieldValues(), savedEntity.getId(), savedEntity.getKey()));
        return savedEntity;
    }

    @Override
    public List<T> getWithFilters(List<Filter> filters) {
        List<CustomField> customFields = customFieldRepository.getAllByKey(entityKey);
        filters = FilterService.validateAndOrderFilters(filters, customFields);
        final List<String> whereStatements = FilterService.formatWhereStatements(filters);
        final List<Object> operands = FilterService.formatOperands(filters);
        String sql = baseQuery + String.join(" ", whereStatements);
        if (filters.stream().anyMatch(Filter::isCustom)) {
            sql = baseQueryJoinCustomFieldValues + String.join(" ", whereStatements);
        }
        List<T> entities = jdbcTemplate.query(sql, rowMapper, operands.toArray());
        for (T entity: entities) {
            setCustomFieldsValuesForEntity(entity);
        }
        return entities;
    }

    @Override
    public T getById(int id) {
        return queryById(id, baseQuery);
    }

    @Override
    public T update(T entity) {
        updateValidation(entity);

        updateImplementation(entity);

        final T savedEntity;
        try {
            savedEntity = getById(entity.getId());
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            //we shouldn't ever reach this block because the database is managing the ID's
            logger.error(ErrorLogs.InsertThenRetrieveError(entityKey, entity.getId()));
            throw new ExceptionInternalCatastrophe(entityKey, entity.getId());
        }

        customFieldValueRepository.upsertValues(entity.getCustomFieldValues(), savedEntity.getId(), savedEntity.getKey());
        return setCustomFieldsValuesForEntity(savedEntity);
    }

    //public void deleteById(int id) will need to be implemented manually

    @Override
    public T getDeletedById(int id) {
        return queryById(id, baseQueryWhereDeletedAtIsNotNull);
    }

    @Override
    public T getByIdIncludeDeleted(int id) {
        return queryById(id, baseQueryIncludeDeleted);
    }

    public T setCustomFieldsValuesForEntity(T entity) {
        entity.setCustomFieldValues(customFieldValueRepository.getCustomFieldValuesByEntityIdAndEntityKey(entity.getId(), entity.getKey()));
        return entity;
    }

    private T queryById(int id, String baseSql) {
        final String sql = baseSql + " AND id = ? ;";
        final T entity;
        try {
            entity = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id},
                    new int[]{Types.BIGINT},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound(entityKey, id);
        }
        return setCustomFieldsValuesForEntity(entity);
    }
}
