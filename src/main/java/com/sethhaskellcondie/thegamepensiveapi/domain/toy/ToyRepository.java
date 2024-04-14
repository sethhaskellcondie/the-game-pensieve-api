package com.sethhaskellcondie.thegamepensiveapi.domain.toy;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
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
    private final String baseQuery = "SELECT * FROM toys WHERE 1 = 1 ";
    private final Logger logger = LoggerFactory.getLogger(SystemRepository.class);
    private final RowMapper<Toy> rowMapper =
            (resultSet, i) ->
                    new Toy(
                            resultSet.getInt("id"),
                            resultSet.getString("name"),
                            resultSet.getString("set")
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
                			INSERT INTO toys(name, set) VALUES (?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, toy.getName());
                    ps.setString(2, toy.getSet());
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
        final String sql = baseQuery + filters + ";";
        return jdbcTemplate.query(sql, rowMapper);
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
        } catch (EmptyResultDataAccessException ignored) { }

        if (toy == null || !toy.isPersisted()) {
            throw new ExceptionResourceNotFound(Toy.class.getSimpleName(), id);
        }
        return toy;
    }

    @Override
    public Toy update(Toy toy) throws ExceptionFailedDbValidation {
        toyDbValidation(toy);
        String sql = """
                			UPDATE toys SET name = ?, set = ? WHERE id = ?;
                """;
        jdbcTemplate.update(
                sql,
                toy.getName(),
                toy.getSet(),
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
                			DELETE FROM toys WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", Toy.class.getSimpleName(), id);
        }
    }

    private void toyDbValidation(Toy toy) throws ExceptionFailedDbValidation {
        //no validation needed for Toy table
    }
}
