package com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy;

import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
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

@Repository
public class ToyRepository extends EntityRepositoryAbstract<Toy, ToyRequestDto, ToyResponseDto> implements EntityRepository<Toy, ToyRequestDto, ToyResponseDto> {

    public ToyRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    protected String getBaseQuery() {
        return """
            SELECT toys.id, toys.name, toys.set, toys.created_at, toys.updated_at, toys.deleted_at
                FROM toys WHERE toys.deleted_at IS NULL
            """;
    }

    protected String getBaseQueryJoinCustomFieldValues() {
        return """
            SELECT toys.id, toys.name, toys.set, toys.created_at, toys.updated_at, toys.deleted_at,
               values.custom_field_id, values.entity_key, values.value_text, values.value_number,
               fields.name as custom_field_name, fields.type as custom_field_type
            FROM toys
            JOIN custom_field_values as values ON toys.id = values.entity_id
            JOIN custom_fields as fields ON values.custom_field_id = fields.id
            WHERE toys.deleted_at IS NULL
            AND values.entity_key = 'toy'
                """;
    }

    protected String getBaseQueryWhereDeletedAtIsNotNull() {
        return """
            SELECT toys.id, toys.name, toys.set, toys.created_at, toys.updated_at, toys.deleted_at
                FROM toys WHERE toys.deleted_at IS NOT NULL
            """;
    }

    protected String getBaseQueryIncludeDeleted() {
        return """
            SELECT toys.id, toys.name, toys.set, toys.created_at, toys.updated_at, toys.deleted_at
                FROM toys WHERE 1 = 1
            """;
    }

    protected String getEntityKey() {
        return Keychain.TOY_KEY;
    }

    protected RowMapper<Toy> getRowMapper() {
        return (resultSet, i) ->
                new Toy(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("set"),
                        resultSet.getTimestamp("created_at"),
                        resultSet.getTimestamp("updated_at"),
                        resultSet.getTimestamp("deleted_at"),
                        new ArrayList<>()
                );
    }

    protected void insertValidation(Toy toy) {
        //no validation needed for Toy table
    }

    @Override
    protected void updateValidation(Toy entity) {
        //no validation needed for Toy table
    }

    protected Integer insertImplementation(Toy toy) {
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
        return (Integer) keyHolder.getKeys().get("id");
    }

    protected void updateImplementation(Toy toy) {
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
    }

    @Override
    public void deleteById(int id) {
        String sql = """
                			UPDATE toys SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", Toy.class.getSimpleName(), id);
        }
    }

    public int getIdByNameAndSet(String name, String set) {
        String sql = getBaseQuery() + " AND name = ? AND set = ?";
        final Toy toy;
        try {
            toy = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{name, set},
                    new int[]{Types.VARCHAR, Types.VARCHAR},
                    getRowMapper()
            );
        } catch (EmptyResultDataAccessException exception) {
            return -1;
        }
        return toy.getId();
    }
}
