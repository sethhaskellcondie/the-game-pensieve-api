package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Types;

@Repository
public class MetadataRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<Metadata> rowMapper = (resultSet, rowNumber) ->
            new Metadata(
                    resultSet.getInt("id"),
                    resultSet.getString("key"),
                    resultSet.getString("value"),
                    resultSet.getTimestamp("created_at").toLocalDateTime(),
                    resultSet.getTimestamp("updated_at").toLocalDateTime(),
                    resultSet.getTimestamp("deleted_at") != null ? resultSet.getTimestamp("deleted_at").toLocalDateTime() : null
            );

    public MetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Metadata getByKey(String key) {
        final String sql = "SELECT * FROM metadata WHERE key = ? AND deleted_at IS NULL";
        Metadata metadata;
        try {
            metadata = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{key},
                    new int[]{Types.VARCHAR},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            throw new ExceptionResourceNotFound("metadata", "key: " + key, exception);
        }
        return metadata;
    }
}