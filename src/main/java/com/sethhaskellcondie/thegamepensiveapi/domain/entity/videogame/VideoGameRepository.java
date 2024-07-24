package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInternalError;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;

@Repository
public class VideoGameRepository extends EntityRepositoryAbstract<VideoGame, VideoGameRequestDto, VideoGameResponseDto>
        implements EntityRepository<VideoGame, VideoGameRequestDto, VideoGameResponseDto> {
    protected VideoGameRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private String getSelectClause() {
        return "SELECT video_games.id, video_games.title, video_games.system_id, video_games.created_at, video_games.updated_at, video_games.deleted_at ";
    }

    protected String getBaseQuery() {
        return getSelectClause() + " FROM video_games WHERE 1 = 1 ";
    }

    @Override
    protected String getBaseQueryJoinCustomFieldValues() {
        return """
                SELECT video_games.id, video_games.title, video_games.system_id,
                       video_games.created_at, video_games.updated_at, video_games.deleted_at
                        FROM video_games
                        JOIN custom_field_values as values ON video_games.id = values.entity_id
                	  	JOIN custom_fields as fields ON values.custom_field_id = fields.id
                               	WHERE video_games.deleted_at IS NULL
                                 AND values.entity_key = 'video_game'
                """;
    }

    @Override
    public void deleteById(int id) {
        final String sql = """
                			UPDATE video_games SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", getEntityKey(), id);
        }
    }

    @Override
    protected String getEntityKey() {
        return Keychain.VIDEO_GAME_KEY;
    }

    @Override
    protected RowMapper<VideoGame> getRowMapper() {
        return (resultSet, rowNumber) -> new VideoGame(
                resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getInt("system_id"),
                resultSet.getTimestamp("created_at"),
                resultSet.getTimestamp("updated_at"),
                resultSet.getTimestamp("deleted_at"),
                new ArrayList<>()
        );
    }

    @Override
    protected void insertValidation(VideoGame entity) {
        if (!entity.isSystemIdValid()) {
            throw new ExceptionInternalError("Error Saving Video Game Entity: the system id was not validated before inserting data into the database. " +
                    "Call createNew from the VideoGameService instead of calling insert() directly on the repository");
        }
    }

    @Override
    protected void updateValidation(VideoGame entity) {
        if (!entity.isSystemIdValid()) {
            throw new ExceptionInternalError("Error Saving Video Game Entity: the system id was not validated before updating the database. " +
                    "Call updateExisting from the VideoGameService instead of calling update() directly on the repository");
        }
    }

    @Override
    protected Integer insertImplementation(VideoGame entity) {
        final String sql = """
                			INSERT INTO video_games(title, system_id, created_at, updated_at) VALUES (?, ?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, entity.getTitle());
                    ps.setInt(2, entity.getSystemId());
                    ps.setTimestamp(3, Timestamp.from(Instant.now()));
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    return ps;
                },
                keyHolder
        );
        return (Integer) keyHolder.getKeys().get("id");
    }

    @Override
    protected void updateImplementation(VideoGame entity) {
        final String sql = """
                UPDATE video_games SET title = ?, system_id = ?, updated_at = ? WHERE id = ?;
                """;
        jdbcTemplate.update(sql, entity.getTitle(), entity.getSystemId(), Timestamp.from(Instant.now()), entity.getId());
    }

    public int getIdByTitleAndSystem(String title, int systemId) {
        final String sql = getBaseQueryExcludeDeleted() + " AND title = ? AND system_id = ?";
        final VideoGame videoGame;
        try {
            videoGame = jdbcTemplate.queryForObject(sql, new Object[]{title, systemId}, new int[]{Types.VARCHAR, Types.BIGINT}, getRowMapper());
        } catch (EmptyResultDataAccessException ignored) {
            return -1;
        }
        return videoGame.getId();
    }
}
