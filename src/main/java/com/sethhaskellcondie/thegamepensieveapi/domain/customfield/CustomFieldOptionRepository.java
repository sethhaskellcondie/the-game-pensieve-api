package com.sethhaskellcondie.thegamepensieveapi.domain.customfield;

import com.sethhaskellcondie.thegamepensieveapi.domain.ErrorLogs;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalCatastrophe;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
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
public class CustomFieldOptionRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Logger logger = LoggerFactory.getLogger(CustomFieldOptionRepository.class);
    private final RowMapper<CustomFieldOption> rowMapper = (resultSet, rowNumber) ->
            new CustomFieldOption(
                    resultSet.getInt("id"),
                    resultSet.getInt("custom_field_id"),
                    resultSet.getString("name"),
                    resultSet.getBoolean("is_default"),
                    resultSet.getInt("display_order")
            );

    public CustomFieldOptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomFieldOption insertOption(int customFieldId, String name, boolean isDefault) {
        final String sql = """
                INSERT INTO custom_field_options(custom_field_id, name, is_default) VALUES (?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setInt(1, customFieldId);
                    ps.setString(2, name);
                    ps.setBoolean(3, isDefault);
                    return ps;
                },
                keyHolder
        );
        final Integer generatedId = (Integer) keyHolder.getKeys().get("id");

        try {
            return getOptionById(generatedId);
        } catch (ExceptionResourceNotFound | NullPointerException e) {
            logger.error(ErrorLogs.insertThenRetrieveError("custom_field_options", generatedId));
            throw new ExceptionInternalCatastrophe("custom_field_options", generatedId, e);
        }
    }

    public List<CustomFieldOption> getOptionsByCustomFieldId(int customFieldId) {
        final String sql = "SELECT * FROM custom_field_options WHERE custom_field_id = ? AND deleted = false ORDER BY display_order ASC, id ASC";
        return jdbcTemplate.query(sql, rowMapper, customFieldId);
    }

    public CustomFieldOption getOptionById(int optionId) {
        final String sql = "SELECT * FROM custom_field_options WHERE id = ? AND deleted = false";
        try {
            return jdbcTemplate.queryForObject(
                    sql,
                    new Object[]{optionId},
                    new int[]{Types.BIGINT},
                    rowMapper
            );
        } catch (EmptyResultDataAccessException e) {
            throw new ExceptionResourceNotFound("custom_field_options", optionId, e);
        }
    }

    public void updateOption(int optionId, String name, int order, boolean isDefault) {
        final String sql = "UPDATE custom_field_options SET name = ?, display_order = ?, is_default = ? WHERE id = ? AND deleted = false";
        jdbcTemplate.update(sql, name, order, isDefault, optionId);
    }

    /**
     * Soft-deletes the option and reassigns all custom_field_values that reference
     * the deleted option's name to the current default option's name.
     */
    public void deleteOption(int optionId, int customFieldId) {
        final String reassignSql = """
                UPDATE custom_field_values
                   SET value_text = (
                       SELECT name FROM custom_field_options
                        WHERE custom_field_id = ? AND is_default = true AND deleted = false
                   )
                 WHERE custom_field_id = ?
                   AND value_text = (SELECT name FROM custom_field_options WHERE id = ?)
                """;
        jdbcTemplate.update(reassignSql, customFieldId, customFieldId, optionId);

        final String deleteSql = "UPDATE custom_field_options SET deleted = true WHERE id = ?";
        int rowsUpdated = jdbcTemplate.update(deleteSql, optionId);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", "custom_field_options", optionId);
        }
    }

    public boolean isValidOptionName(int customFieldId, String name) {
        final String sql = "SELECT COUNT(*) FROM custom_field_options WHERE custom_field_id = ? AND name = ? AND deleted = false";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, customFieldId, name);
        return count != null && count > 0;
    }
}
