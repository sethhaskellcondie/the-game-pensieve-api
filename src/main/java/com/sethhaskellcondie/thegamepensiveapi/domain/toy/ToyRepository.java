package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionMalformedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.sethhaskellcondie.thegamepensiveapi.domain.system.SystemRepository;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ErrorLogs;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

@Repository
public class ToyRepository implements EntityRepository<Toy, ToyRequestDto, ToyResponseDto> {
    private final JdbcTemplate jdbcTemplate;
    private final String baseQuery = "SELECT * FROM toys WHERE deleted_at IS NULL";
    private final Logger logger = LoggerFactory.getLogger(SystemRepository.class);
    private final RowMapper<Toy> rowMapper =
            (resultSet, i) ->
                    new Toy(
                            resultSet.getInt("id"),
                            resultSet.getString("name"),
                            resultSet.getString("set"),
                            resultSet.getTimestamp("created_at"),
                            resultSet.getTimestamp("updated_at"),
                            resultSet.getTimestamp("deleted_at")
                    );

    public ToyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Toy insert(ToyRequestDto requestDto) throws ExceptionMalformedEntity, ExceptionFailedDbValidation {
        final Toy toy = new Toy().updateFromRequestDto(requestDto);
        return this.insert(toy);
    }

    @Override
    public Toy insert(Toy toy) throws ExceptionFailedDbValidation {
        toyDbValidation(toy);
        final String sql = """
                			INSERT INTO toys(name, set, created_at, updated_at) VALUES (?, ?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, toy.getName());
                    ps.setString(2, toy.getSet());
                    ps.setTimestamp(3, Timestamp.from(Instant.now()));
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    return ps;
                },
                keyHolder
        );
        final Integer generatedId = (Integer) keyHolder.getKeys().get("id");

        try {
            return getById(generatedId);
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            //we shouldn't ever reach this block because the database is managing the ID's
            logger.error(ErrorLogs.InsertThenRetrieveError(toy.getClass().getSimpleName(), generatedId));
            throw new ExceptionInternalCatastrophe(toy.getClass().getSimpleName(), generatedId);
        }
    }

    @Override
    public List<Toy> getWithFilters(List<Filter> filters) {
        Filter.validateFilters(filters);
        final List<String> whereStatements = Filter.formatWhereStatements(filters);
        final List<Object> operands = Filter.formatOperands(filters);
        final String sql = baseQuery + String.join(" ", whereStatements);
        return jdbcTemplate.query(sql, rowMapper, operands.toArray());
    }

    @Override
    public Toy getById(int id) throws ExceptionResourceNotFound {
        final String sql = baseQuery + " AND id = ? ;";
        Toy toy = null;
        try {
            toy = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id},
                    new int[]{Types.BIGINT},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound(Toy.class.getSimpleName(), id);
        }
        return toy;
    }

    @Override
    public Toy update(Toy toy) throws ExceptionFailedDbValidation {
        toyDbValidation(toy);
        String sql = """
                			UPDATE toys SET name = ?, set = ?, updated_at = ? WHERE id = ?;
                """;
        jdbcTemplate.update(
                sql,
                toy.getName(),
                toy.getSet(),
                Timestamp.from(Instant.now()),
                toy.getId()
        );

        try {
            return getById(toy.getId());
        } catch (ExceptionResourceNotFound e) {
            //we shouldn't ever reach this block of code
            logger.error(ErrorLogs.UpdateThenRetrieveError(toy.getClass().getSimpleName(), toy.getId()));
            throw new ExceptionInternalCatastrophe(toy.getClass().getSimpleName(), toy.getId());
        }
    }

    @Override
    public void deleteById(int id) throws ExceptionResourceNotFound {
        String sql = """
                			UPDATE toys SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", Toy.class.getSimpleName(), id);
        }
    }

    @Override
    public Toy getDeletedById(int id) throws ExceptionResourceNotFound {
        final String sql = "SELECT * FROM toys WHERE id = ? AND deleted_at IS NOT NULL;";
        Toy toy = null;
        try {
            toy = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{id},
                    new int[]{Types.BIGINT},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound(Toy.class.getSimpleName(), id);
        }
        return toy;
    }

    private void toyDbValidation(Toy toy) throws ExceptionFailedDbValidation {
        //no validation needed for Toy table
    }
}
