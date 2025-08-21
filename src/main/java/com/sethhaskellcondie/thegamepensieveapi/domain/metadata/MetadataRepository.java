package com.sethhaskellcondie.thegamepensieveapi.domain.metadata;

import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
public class MetadataRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(MetadataRepository.class);
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
            throw new ExceptionResourceNotFound("No Metadata found with key: + key: " + key, exception);
        }
        return metadata;
    }

    public Metadata insertMetadata(Metadata metadata) {
        final String sql = """
                            INSERT INTO metadata(key, value) VALUES (?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(
                    connection -> {
                        PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                        ps.setString(1, metadata.key());
                        ps.setString(2, metadata.value());
                        return ps;
                    },
                    keyHolder
            );
        } catch (DataIntegrityViolationException e) {
            throw new ExceptionFailedDbValidation("Metadata with key '" + metadata.key() + "' already exists, Metadata keys must be unique.");
        }

        try {
            return getByKey(metadata.key());
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            logger.error("Database State Error: Just inserted metadata with key '" + metadata.key() + "' and couldn't immediately retrieve it.");
            throw new ExceptionInternalCatastrophe("Just inserted metadata with key '" + metadata.key() + "' and couldn't immediately retrieve it.");
        }
    }

    public List<Metadata> getAllMetadata() {
        final String sql = "SELECT * FROM metadata WHERE deleted_at IS NULL";
        return jdbcTemplate.query(sql, rowMapper);
    }

    private Metadata getDeletedByKey(String key) {
        final String sql = "SELECT * FROM metadata WHERE key = ? AND deleted_at IS NOT NULL";
        Metadata metadata;
        try {
            metadata = jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{key},
                    new int[]{Types.VARCHAR},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
        return metadata;
    }

    public Metadata updateValue(Metadata metadata) {
        try {
            getByKey(metadata.key());
        } catch (ExceptionResourceNotFound e) {
            throw new ExceptionResourceNotFound("No metadata found with key: " + metadata.key(), e);
        }

        final String sql = """
                            UPDATE metadata SET value = ?, deleted_at = NULL, updated_at = now() WHERE key = ?;
                """;
        jdbcTemplate.update(sql, metadata.value(), metadata.key());
        return getByKey(metadata.key());
    }
}