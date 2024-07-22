package com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.videogame.VideoGame;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionInternalError;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensiveapi.domain.filter.Filter;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.Integer.valueOf;

@Repository
public class VideoGameBoxRepository extends EntityRepositoryAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityRepository<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {
    protected VideoGameBoxRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String getBaseQuery() {
        return """
                SELECT id, title, system_id, is_physical, is_collection, created_at, updated_at, deleted_at
                FROM video_game_boxes
                WHERE video_game_boxes.deleted_at IS NULL
                """;
    }

    @Override
    protected String getBaseQueryJoinCustomFieldValues() {
        return """
                SELECT video_game_boxes.id, video_game_boxes.title, video_game_boxes.is_physical, video_game_boxes.is_collection,
                video_game_boxes.created_at, video_game_boxes.updated_at, video_game_boxes.deleted_at
                FROM video_game_boxes
                JOIN custom_field_values as values ON video_game_boxes.id = values.entity_id
                JOIN custom_fields as fields ON values.custom_field_id = fields.id
                WHERE video_game_boxes.deleted_at IS NULL
                AND values.entity_key = 'video_game_box'
                AND video_game_boxes.deleted_at IS NULL
                """;
    }

    @Override
    protected String getBaseQueryWhereDeletedAtIsNotNull() {
        return """
                SELECT video_game_boxes.id, video_game_boxes.title, video_game_boxes.is_physical, video_game_boxes.is_collection,
                video_game_boxes.created_at, video_game_boxes.updated_at, video_game_boxes.deleted_at
                FROM video_game_boxes
                WHERE video_game_boxes.deleted_at IS NOT NULL
                """;
    }

    @Override
    protected String getBaseQueryIncludeDeleted() {
        return """
                SELECT video_game_boxes.id, video_game_boxes.title, video_game_boxes.is_physical, video_game_boxes.is_collection,
                video_game_boxes.created_at, video_game_boxes.updated_at, video_game_boxes.deleted_at
                FROM video_game_boxes
                WHERE 1 = 1
                """;
    }

    @Override
    public List<VideoGameBox> getWithFilters(List<Filter> filters) {
        List<VideoGameBox> boxesWithNoIds = super.getWithFilters(filters);
        List<VideoGameBox> boxesWithIds = new ArrayList<>();
        for (VideoGameBox gameBox : boxesWithNoIds) {
            gameBox.setVideoGameIds(getVideoGameIdsForBoxId(gameBox.getId()));
            boxesWithIds.add(gameBox);
        }
        return boxesWithIds;
    }

    @Override
    public VideoGameBox getById(int id) {
        VideoGameBox videoGameBox = super.getById(id);
        videoGameBox.setVideoGameIds(getVideoGameIdsForBoxId(id));
        return videoGameBox;
    }

    @Override
    public void deleteById(int id) {
        final String sql = """
                UPDATE video_game_boxes SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", getEntityKey(), id);
        }
    }

    @Override
    protected String getEntityKey() {
        return Keychain.VIDEO_GAME_BOX_KEY;
    }

    @Override
    protected RowMapper<VideoGameBox> getRowMapper() {
        return (resultSet, rowNumber) -> new VideoGameBox(
                resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getInt("system_id"),
                resultSet.getBoolean("is_physical"),
                resultSet.getBoolean("is_collection"),
                resultSet.getTimestamp("created_at"),
                resultSet.getTimestamp("updated_at"),
                resultSet.getTimestamp("deleted_at"),
                new ArrayList<>()
        );
    }

    @Override
    protected void insertValidation(VideoGameBox videoGameBox) {
        if (!videoGameBox.isSystemIdValid() || !videoGameBox.isVideoGamesValid()) {
            throw new ExceptionInternalError("Error Persisting Video Game Box Entity: The system_id and video game list must be validated before inserting into the database. "
            + "Call createNew from the VideoGameBoxService instead of calling insert() directly on the repository.");
        }
    }

    @Override
    protected void updateValidation(VideoGameBox videoGameBox) {
        if (!videoGameBox.isSystemIdValid() || !videoGameBox.isVideoGamesValid()) {
            throw new ExceptionInternalError("Error Persisting Video Game Box Entity: The system_id and video game list must be validated before updating the database. "
                    + "Call updateExisting from the VideoGameBoxService instead of calling update() directly on the repository.");
        }
    }

    @Override
    protected Integer insertImplementation(VideoGameBox videoGameBox) {
        final String sql = """
                INSERT INTO video_game_boxes(title, system_id, is_physical, is_collection, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, videoGameBox.getTitle());
                    ps.setInt(2, videoGameBox.getSystemId());
                    ps.setBoolean(3, videoGameBox.isPhysical());
                    ps.setBoolean(4, videoGameBox.isCollection());
                    ps.setTimestamp(5, Timestamp.from(Instant.now()));
                    ps.setTimestamp(6, Timestamp.from(Instant.now()));
                    return ps;
                },
                keyHolder
        );

        int videoGameBoxId = (Integer) keyHolder.getKeys().get("id");
        for (VideoGame videoGame : videoGameBox.getVideoGames()) {
            insertRelationshipBetweenGameAndBox(videoGame.getId(), videoGameBoxId);
        }

        return videoGameBoxId;
    }

    @Override
    protected void updateImplementation(VideoGameBox videoGameBox) {
        final String sql = """
                UPDATE video_game_boxes SET title = ?, system_id = ?, is_physical = ?, is_collection = ?, updated_at = ? WHERE id = ?;
                """;
        jdbcTemplate.update(sql, videoGameBox.getTitle(), videoGameBox.getSystemId(), videoGameBox.isPhysical(), videoGameBox.isCollection(), Timestamp.from(Instant.now()), videoGameBox.getId());

        final String joinTableSelect = """
                SELECT video_game_id FROM video_game_to_video_game_box WHERE video_game_box_id = ?;
                """;
        List<Integer> gameListIds = jdbcTemplate.query(
                joinTableSelect,
                (resultSet, rowNumber) -> valueOf(resultSet.getInt("video_game_id")),
                videoGameBox.getId()
        );
        for (VideoGame videoGame : videoGameBox.getVideoGames()) {
            if (!gameListIds.contains(videoGame.getId())) {
                insertRelationshipBetweenGameAndBox(videoGame.getId(), videoGameBox.getId());
            }
            gameListIds.remove(videoGame.getId());
        }
        if (!gameListIds.isEmpty()) {
            final String inClause = String.join(",", Collections.nCopies(gameListIds.size(), "?"));
            final String deleteSql = String.format("DELETE FROM video_game_to_video_game_box WHERE video_game_id IN (%s)", inClause);
            final Object[] args = gameListIds.toArray();
            int[] argTypes = new int[gameListIds.size()];
            Arrays.fill(argTypes, Types.INTEGER);
            jdbcTemplate.update(deleteSql, args, argTypes);
        }
    }

    public int getIdByTitleAndSystem(String title, int systemId) {
        final String sql = getBaseQuery() + " AND title = ? AND system_id = ?";
        final VideoGameBox videoGameBox;
        try {
            videoGameBox = jdbcTemplate.queryForObject(sql, new Object[]{title, systemId}, new int[]{Types.VARCHAR, Types.BIGINT}, getRowMapper());
        } catch (EmptyResultDataAccessException ignored) {
            return -1;
        }
        return videoGameBox.getId();
    }

    private List<Integer> getVideoGameIdsForBoxId(int boxId) {
        final String sql = """
                SELECT * FROM video_game_to_video_game_box WHERE video_game_box_id = ?;
                """;
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getInt("video_game_id"), boxId);
    }

    private void insertRelationshipBetweenGameAndBox(int videoGameId, int videoGameBoxId) {
        final String sql = """
                INSERT INTO video_game_to_video_game_box(video_game_id, video_game_box_id)
                    VALUES(?, ?);
                """;
        jdbcTemplate.update(sql, videoGameId, videoGameBoxId);
    }
}
