package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
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
import java.util.HashMap;
import java.util.List;

@Repository
public class SystemRepository implements EntityRepository<System, SystemRequestDto, SystemResponseDto> {
    private final JdbcTemplate jdbcTemplate;
    private final String baseQuery = "SELECT * FROM systems WHERE deleted_at IS NULL";
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
                    new HashMap<>(), //TODO update this
                    new HashMap<>()  //TODO update this
            );

    public SystemRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public System insert(SystemRequestDto requestDto) throws ExceptionMalformedEntity, ExceptionFailedDbValidation {
        final System system = new System().updateFromRequestDto(requestDto);
        return this.insert(system);
    }

    @Override
    public System insert(System system) throws ExceptionFailedDbValidation {
        // ---to change this into an upsert
        // if (requestDto.isPersisted()) {
        // 		return update(requestDto);
        // }

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
        Integer generatedId = (Integer) keyHolder.getKeys().get("id");

        try {
            return getById(generatedId);
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            // we shouldn't ever reach this block of code because the database is managing the ids
            // so if we do throw a disaster
            logger.error(ErrorLogs.InsertThenRetrieveError(system.getClass().getSimpleName(), generatedId));
            throw new ExceptionInternalCatastrophe(system.getClass().getSimpleName(), generatedId);
        }
    }

    @Override
    public List<System> getWithFilters(List<Filter> filters) {
        filters = Filter.validateAndOrderFilters(filters);
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = baseQuery + String.join(" ", whereStatements);
        return jdbcTemplate.query(sql, rowMapper, operands.toArray());
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
            throw new ExceptionResourceNotFound(System.class.getSimpleName(), id);
        }
        return system;
    }

    @Override
    public System update(System system) throws ExceptionFailedDbValidation, ExceptionInvalidFilter {
        // ---to change this into an upsert
        // if (!system.isPersisted()) {
        // 		return insert(system);
        // }

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

        try {
            return getById(system.getId());
        } catch (ExceptionResourceNotFound e) {
            // we shouldn't ever reach this block of code because the database is managing the ids
            // but if we do then we better log it and throw a disaster
            logger.error(ErrorLogs.UpdateThenRetrieveError(system.getClass().getSimpleName(), system.getId()));
            throw new ExceptionInternalCatastrophe(system.getClass().getSimpleName(), system.getId());
        }
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

        return system;
    }

    //This method will be commonly used to validate objects before they are inserted or updated,
    //performing any validation that is not enforced by the database schema
    private void systemDbValidation(System system) throws ExceptionFailedDbValidation, ExceptionInvalidFilter {
        Filter nameFilter = new Filter("system", "name", "equals", system.getName());
        final List<System> existingSystems = getWithFilters(List.of(nameFilter));
        if (!existingSystems.isEmpty()) {
            throw new ExceptionFailedDbValidation("System insert/update failed, duplicate name found.");
        }
    }
}
