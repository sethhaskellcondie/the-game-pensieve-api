package com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogamebox;

import com.sethhaskellcondie.thegamepensieveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.EntityRepositoryAbstract;
import com.sethhaskellcondie.thegamepensieveapi.domain.entity.videogame.SlimVideoGame;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionInternalError;
import com.sethhaskellcondie.thegamepensieveapi.domain.exceptions.ExceptionResourceNotFound;
import com.sethhaskellcondie.thegamepensieveapi.domain.filter.Filter;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Integer.valueOf;

@Repository
public class VideoGameBoxRepository extends EntityRepositoryAbstract<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto>
        implements EntityRepository<VideoGameBox, VideoGameBoxRequestDto, VideoGameBoxResponseDto> {
    protected VideoGameBoxRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private String getSelectClause() {
        return "SELECT video_game_boxes.id, video_game_boxes.title, video_game_boxes.system_id, video_game_boxes.is_physical, video_game_boxes.is_collection, "
                + "video_game_boxes.created_at, video_game_boxes.updated_at, video_game_boxes.deleted_at ";
    }

    protected String getBaseQuery(boolean includeWhereClause) {
        if (includeWhereClause) {
            return getSelectClause() + " FROM video_game_boxes WHERE 1 = 1 ";
        }
        return getSelectClause() + " FROM video_game_boxes";
    }

    @Override
    public List<VideoGameBox> getWithFilters(List<Filter> filters) {
        final List<VideoGameBox> boxes = super.getWithFilters(filters);
        setRelatedVideoGameIds(boxes);
        return boxes;
    }

    @Override
    public List<VideoGameBox> getByIds(List<Integer> ids) {
        final List<VideoGameBox> boxes = super.getByIds(ids);
        setRelatedVideoGameIds(boxes);
        return boxes;
    }

    //Batch load the related game ids for every box in one query (instead of one query per box) to avoid N+1 queries.
    //The game ids are needed so that each box recomputes is_collection from its actual game count (see setVideoGameIds).
    private void setRelatedVideoGameIds(List<VideoGameBox> boxes) {
        if (boxes.isEmpty()) {
            return;
        }
        final List<Integer> boxIds = boxes.stream().map(VideoGameBox::getId).toList();
        final String placeholders = String.join(", ", Collections.nCopies(boxIds.size(), "?"));
        final String sql = "SELECT video_game_box_id, video_game_id FROM video_game_to_video_game_box WHERE video_game_box_id IN ("
                + placeholders + ") ORDER BY video_game_id";
        final List<int[]> pairs = jdbcTemplate.query(sql,
                (resultSet, rowNumber) -> new int[]{resultSet.getInt("video_game_box_id"), resultSet.getInt("video_game_id")},
                boxIds.toArray());
        final Map<Integer, List<Integer>> gameIdsByBoxId = new HashMap<>();
        for (int[] pair : pairs) {
            gameIdsByBoxId.computeIfAbsent(pair[0], key -> new ArrayList<>()).add(pair[1]);
        }
        for (VideoGameBox box : boxes) {
            box.setVideoGameIds(gameIdsByBoxId.getOrDefault(box.getId(), new ArrayList<>()));
        }
    }

    @Override
    public VideoGameBox getById(int id) {
        VideoGameBox videoGameBox = super.getById(id);
        videoGameBox.setVideoGameIds(getVideoGameIdsForBoxId(id));
        return videoGameBox;
    }

    @Override
    @Transactional
    public void deleteById(int id) {
        final String sql = """
                UPDATE video_game_boxes SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", getEntityKey(), id);
        }
        final String sql2 = """
                DELETE FROM video_game_to_video_game_box WHERE video_game_box_id = ?;
                """;
        jdbcTemplate.update(sql2, id);
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
        if (!videoGameBox.isSystemValid() || !videoGameBox.isVideoGamesValid()) {
            throw new ExceptionInternalError("Error Persisting Video Game Box Entity: The system_id and video game list must be validated before inserting into the database. "
            + "Call createNew from the VideoGameBoxService instead of calling insert() directly on the repository.");
        }
    }

    @Override
    protected void updateValidation(VideoGameBox videoGameBox) {
        if (!videoGameBox.isSystemValid() || !videoGameBox.isVideoGamesValid()) {
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
        for (SlimVideoGame videoGame : videoGameBox.getVideoGames()) {
            insertRelationshipBetweenGameAndBox(videoGame.id(), videoGameBoxId);
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
        for (SlimVideoGame videoGame : videoGameBox.getVideoGames()) {
            if (!gameListIds.contains(videoGame.id())) {
                insertRelationshipBetweenGameAndBox(videoGame.id(), videoGameBox.getId());
            }
            gameListIds.remove(videoGame.id());
        }
        if (!gameListIds.isEmpty()) {
            final String inClause = String.join(",", Collections.nCopies(gameListIds.size(), "?"));
            final String deleteSql = String.format("DELETE FROM video_game_to_video_game_box WHERE video_game_box_id = ? AND video_game_id IN (%s)", inClause);
            final Object[] args = new Object[gameListIds.size() + 1];
            final int[] argTypes = new int[gameListIds.size() + 1];
            args[0] = videoGameBox.getId();
            argTypes[0] = Types.INTEGER;
            for (int i = 0; i < gameListIds.size(); i++) {
                args[i + 1] = gameListIds.get(i);
                argTypes[i + 1] = Types.INTEGER;
            }
            jdbcTemplate.update(deleteSql, args, argTypes);
        }
    }

    public int getIdByTitleAndSystem(String title, int systemId) {
        final String sql = getBaseQueryExcludeDeleted() + " AND title = ? AND system_id = ?";
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
