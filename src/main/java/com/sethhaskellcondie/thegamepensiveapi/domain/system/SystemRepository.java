package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValueRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ErrorLogs;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInvalidFilter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class SystemRepository implements EntityRepository<System, SystemRequestDto, SystemResponseDto> {
    private final JdbcTemplate jdbcTemplate;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final String baseQuery = """
            SELECT systems.id, systems.name, systems.generation, systems.handheld, systems.created_at, systems.updated_at, systems.deleted_at
            FROM systems WHERE deleted_at IS NULL
            """;
    //in general the first row are the columns for the entity
    //the second and third row are the columns for the custom field values and the custom fields they should always be the same
    //then the remainder of the query is the same but fill in the property entity (systems)
    private final String baseQueryWithCustomFields = """
        SELECT systems.id, systems.name, systems.generation, systems.handheld, systems.created_at, systems.updated_at, systems.deleted_at,
               values.custom_field_id, values.entity_key, values.value_text, values.value_number,
               fields.name as custom_field_name, fields.type as custom_field_type
            FROM systems
            JOIN custom_field_values as values ON systems.id = values.entity_id
            JOIN custom_fields as fields ON values.custom_field_id = fields.id
            WHERE systems.deleted_at IS NULL
            AND values.entity_key = 'system'
        """;
    private final Logger logger = LoggerFactory.getLogger(SystemRepository.class);
    private final RowMapper<System> rowMapper = (resultSet, rowNumber) ->
            new System(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getInt("generation"),
                    resultSet.getBoolean("handheld"),
                    resultSet.getTimestamp("created_at"),
                    resultSet.getTimestamp("updated_at"),
                    resultSet.getTimestamp("deleted_at"),
                    new ArrayList<>()
            );

    public SystemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.customFieldValueRepository = new CustomFieldValueRepository(jdbcTemplate);
    }

    @Override
    public System insert(SystemRequestDto requestDto) throws ExceptionMalformedEntity, ExceptionFailedDbValidation {
        final System system = new System().updateFromRequestDto(requestDto);
        return this.insert(system);
    }

    @Override
    public System insert(System system) throws ExceptionFailedDbValidation {
        systemDbValidation(system);

        final String sql = """
                			INSERT INTO systems(name, generation, handheld, created_at, updated_at) VALUES (?, ?, ?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();

        // This update call will take a preparedStatementCreator and a KeyHolder,
        // the preparedStatementCreator takes a connection, the connection can
        // include a Statement to hold the generated key and then put them in the
        // KeyHolder.
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, system.getName());
                    ps.setInt(2, system.getGeneration());
                    ps.setBoolean(3, system.isHandheld());
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.setTimestamp(5, Timestamp.from(Instant.now()));
                    return ps;
                },
                keyHolder
        );
        final Integer generatedId = (Integer) keyHolder.getKeys().get("id");

        System savedSystem;
        try {
            savedSystem = getById(generatedId);
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            // we shouldn't ever reach this block of code because the database is managing the ids
            // so if we do throw a disaster
            logger.error(ErrorLogs.InsertThenRetrieveError(system.getClass().getSimpleName(), generatedId));
            throw new ExceptionInternalCatastrophe(system.getClass().getSimpleName(), generatedId);
        }
        savedSystem.setCustomFieldValues(customFieldValueRepository.upsertValues(system.getCustomFieldValues(), savedSystem.getId(), savedSystem.getKey()));
        return savedSystem;
    }

    @Override
    public List<System> getWithFilters(List<Filter> filters) {
        filters = Filter.validateAndOrderFilters(filters);
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        String sql = baseQuery + String.join(" ", whereStatements);
        if (filters.stream().anyMatch(Filter::isCustom)) {
            sql = baseQueryWithCustomFields + String.join(" ", whereStatements);
        }
        List<System> systems = jdbcTemplate.query(sql, rowMapper, operands.toArray());
        for (System system: systems) {
            system.setCustomFieldValues(customFieldValueRepository.getCustomFieldsByEntityIdAndEntityKey(system.getId(), system.getKey()));
        }
        return systems;
    }

    @Override
    public System getById(int id) throws ExceptionResourceNotFound {
        final String sql = baseQuery + " AND id = ? ;";
        System system = null;
        try {
            system = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id}, //args to bind to the sql ?
                    new int[]{Types.BIGINT}, //the types of the objects to bind to the sql
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound(Keychain.SYSTEM_KEY, id);
        }
        system.setCustomFieldValues(customFieldValueRepository.getCustomFieldsByEntityIdAndEntityKey(system.getId(), system.getKey()));
        return system;
    }

    @Override
    public System update(System system) throws ExceptionFailedDbValidation, ExceptionInvalidFilter {
        systemDbValidation(system);
        final String sql = """
                			UPDATE systems SET name = ?, generation = ?, handheld = ?, updated_at = ? WHERE id = ?;
                """;
        jdbcTemplate.update(
                sql,
                system.getName(),
                system.getGeneration(),
                system.isHandheld(),
                Timestamp.from(Instant.now()),
                system.getId()
        );

        System updatedSystem;
        try {
            updatedSystem = getById(system.getId());
        } catch (ExceptionResourceNotFound e) {
            // we shouldn't ever reach this block of code because the database is managing the ids
            // but if we do then we better log it and throw a disaster
            logger.error(ErrorLogs.UpdateThenRetrieveError(system.getClass().getSimpleName(), system.getId()));
            throw new ExceptionInternalCatastrophe(system.getClass().getSimpleName(), system.getId());
        }
        updatedSystem.setCustomFieldValues(customFieldValueRepository.upsertValues(system.getCustomFieldValues(), updatedSystem.getId(), updatedSystem.getKey()));
        return updatedSystem;
    }

    @Override
    public void deleteById(int id) throws ExceptionResourceNotFound {
        final String sql = """
                			UPDATE systems SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", System.class.getSimpleName(), id);
        }
    }

    @Override
    public System getDeletedById(int id) throws ExceptionResourceNotFound {
        final String sql = "SELECT * FROM systems WHERE id = ? AND deleted_at IS NOT NULL";
        System system = null;
        try {
            system = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id},
                    new int[]{Types.BIGINT},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound(System.class.getSimpleName(), id);
        }
        system.setCustomFieldValues(customFieldValueRepository.getCustomFieldsByEntityIdAndEntityKey(system.getId(), system.getKey()));
        return system;
    }

    //This method will be commonly used to validate objects before they are inserted or updated,
    //performing any validation that is not enforced by the database schema
    private void systemDbValidation(System system) throws ExceptionFailedDbValidation, ExceptionInvalidFilter {
        Filter nameFilter = new Filter("system", Filter.FIELD_TYPE_TEXT, "name", "equals", system.getName(), false);
        final List<System> existingSystems = getWithFilters(List.of(nameFilter));
        if (!existingSystems.isEmpty()) {
            throw new ExceptionFailedDbValidation("System insert/update failed, duplicate name found.");
        }
    }
}
