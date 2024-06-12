package com.sethhaskellcondie.thegamepensiveapi.domain;

import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValueRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ErrorLogs;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Types;
import java.util.List;

public abstract class EntityRepositoryAbstract<T extends Entity<RequestDto, ResponseDto>, RequestDto, ResponseDto> implements EntityRepository<T, RequestDto, ResponseDto> {

    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final String baseQuery;
    private final String baseQueryJoinCustomFieldValues;
    private final String baseQueryWhereDeletedAtIsNotNull;
    private final String entityKey;
    private final RowMapper<T> rowMapper;
    private final Logger logger = LoggerFactory.getLogger(EntityRepositoryAbstract.class);


    protected EntityRepositoryAbstract(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldValueRepository = new CustomFieldValueRepository(jdbcTemplate);
        this.baseQuery = this.getBaseQuery();
        this.baseQueryJoinCustomFieldValues = this.getBaseQueryJoinCustomFieldValues();
        this.baseQueryWhereDeletedAtIsNotNull = this.getBaseQueryWhereDeletedAtIsNotNull();
        this.entityKey = this.getEntityKey();
        this.rowMapper = this.getRowMapper();
    }

    abstract String getBaseQuery();
    abstract String getBaseQueryJoinCustomFieldValues();
    abstract String getBaseQueryWhereDeletedAtIsNotNull();
    abstract String getEntityKey();
    abstract RowMapper<T> getRowMapper();

    abstract void dbValidation(T entity);
    abstract Integer insertImplementation(T entity);
    abstract void updateImplementation(T entity);

    //public T insert (RequestDto requestDto) will need to implemented manually in each repository

    @Override
    public T insert(T entity) {
        dbValidation(entity);

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
        filters = Filter.validateAndOrderFilters(filters);
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        String sql = baseQuery + String.join(" ", whereStatements);
        if (filters.stream().anyMatch(Filter::isCustom)) {
            sql = baseQueryJoinCustomFieldValues + String.join(" ", whereStatements);
        }
        List<T> entities = jdbcTemplate.query(sql, rowMapper, operands.toArray());
        for (Entity<RequestDto, ResponseDto> entity: entities) {
            entity.setCustomFieldValues(customFieldValueRepository.getCustomFieldValuesByEntityIdAndEntityKey(entity.getId(), entity.getKey()));
        }
        return entities;
    }

    @Override
    public T getById(int id) {
        return queryById(id, baseQuery);
    }

    @Override
    public T update(T entity) {
        dbValidation(entity);

        updateImplementation(entity);

        final T savedEntity;
        try {
            savedEntity = getById(entity.getId());
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            //we shouldn't ever reach this block because the database is managing the ID's
            logger.error(ErrorLogs.InsertThenRetrieveError(entityKey, entity.getId()));
            throw new ExceptionInternalCatastrophe(entityKey, entity.getId());
        }
        savedEntity.setCustomFieldValues(customFieldValueRepository.upsertValues(entity.getCustomFieldValues(), savedEntity.getId(), savedEntity.getKey()));
        return savedEntity;
    }

    //public void deleteById(int id) will need to be implemented manually

    @Override
    public T getDeletedById(int id) {
        return queryById(id, baseQueryWhereDeletedAtIsNotNull);
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
        entity.setCustomFieldValues(customFieldValueRepository.getCustomFieldValuesByEntityIdAndEntityKey(entity.getId(), entity.getKey()));
        return entity;
    }
}
