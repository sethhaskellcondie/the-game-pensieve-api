package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ErrorLogs;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalCatastrophe;
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
import java.sql.Types;
import java.util.List;

@Repository
public class SystemRepositoryImpl implements SystemRepository {
    private final JdbcTemplate jdbcTemplate;
    private final String baseQuery = "SELECT * FROM systems WHERE 1 = 1 "; //include a WHERE 1 = 1 clause at the end, so we can always append with AND
    private final Logger logger = LoggerFactory.getLogger(SystemRepositoryImpl.class);
    private final RowMapper<System> rowMapper = (resultSet, rowNumber) ->
            new System(
                    resultSet.getInt("id"),
                    resultSet.getString("name"),
                    resultSet.getInt("generation"),
                    resultSet.getBoolean("handheld")
            );

    public SystemRepositoryImpl(JdbcTemplate jdbcTemplate) {
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
                			INSERT INTO systems(name, generation, handheld) VALUES (?, ?, ?);
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
    public List<System> getWithFilters(String filters) {
        final String sql = baseQuery + filters + ";";
        return jdbcTemplate.query(sql, rowMapper);
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
                    rowMapper);
        } catch (EmptyResultDataAccessException ignored) { }

        if (system == null || !system.isPersisted()) {
            throw new ExceptionResourceNotFound(System.class.getSimpleName(), id);
        }
        return system;
    }

    @Override
    public System update(System system) throws ExceptionFailedDbValidation {
        // ---to change this into an upsert
        // if (!system.isPersisted()) {
        // 		return insert(system);
        // }

        systemDbValidation(system);
        final String sql = """
                			UPDATE systems SET name = ?, generation = ?, handheld = ? WHERE id = ?;
                """;
        jdbcTemplate.update(
                sql,
                system.getName(),
                system.getGeneration(),
                system.isHandheld(),
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
                			DELETE FROM systems WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", System.class.getSimpleName(), id);
        }
    }

    //This method will be commonly used to validate objects before they are inserted or updated,
    //performing any validation that is not enforced by the database schema
    private void systemDbValidation(System system) throws ExceptionFailedDbValidation {
        final List<System> existingSystems = getWithFilters(" AND name = '" + system.getName() + "'");
        if (!existingSystems.isEmpty()) {
            throw new ExceptionFailedDbValidation("System write failed, duplicate name found.");
        }
    }
}
