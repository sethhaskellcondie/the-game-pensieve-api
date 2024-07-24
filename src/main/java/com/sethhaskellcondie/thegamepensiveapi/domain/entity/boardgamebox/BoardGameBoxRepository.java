package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
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
import java.util.List;

@Repository
public class BoardGameBoxRepository extends EntityRepositoryAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityRepository<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {
    protected BoardGameBoxRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    private String getSelectClause() {
        return "SELECT board_game_boxes.id, board_game_boxes.title, board_game_boxes.is_expansion, board_game_boxes.is_stand_alone, board_game_boxes.base_set_id, board_game_boxes.board_game_id, " +
                "board_game_boxes.created_at, board_game_boxes.updated_at, board_game_boxes.deleted_at";
    }

    protected String getBaseQuery() {
        return getSelectClause() + " FROM board_game_boxes WHERE 1 = 1 ";
    }

    protected String getBaseQueryExcludeDeleted() {
        return getBaseQuery() + " AND deleted_at IS NULL ";
    }

    protected String getBaseQueryWhereIsDeleted() {
        return getBaseQuery() + " AND deleted_at IS NOT NULL ";
    }

    @Override
    protected String getBaseQueryJoinCustomFieldValues() {
        return """
                SELECT board_game_boxes.id, board_game_boxes.title, board_game_boxes.is_expansion, board_game_boxes.is_stand_alone, board_game_boxes.base_set_id, board_game_boxes.board_game_id,
                board_game_boxes.created_at, board_game_boxes.updated_at, board_game_boxes.deleted_at
                FROM board_game_boxes
                JOIN custom_field_values as values ON board_game_boxes.id = values.entity_id
                	  	JOIN custom_fields as fields ON values.custom_field_id = fields.id
                               	WHERE board_game_boxes.deleted_at IS NULL
                                 AND values.entity_key = 'board_game_box'
                """;
    }

    @Override
    public void deleteById(int id) {
        final String sql = """
                			UPDATE board_game_boxes SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", getEntityKey(), id);
        }
    }

    @Override
    protected String getEntityKey() {
        return Keychain.BOARD_GAME_BOX_KEY;
    }

    @Override
    protected RowMapper<BoardGameBox> getRowMapper() {
        return (resultSet, rowNumber) -> new BoardGameBox(
                resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getBoolean("is_expansion"),
                resultSet.getBoolean("is_stand_alone"),
                resultSet.getInt("base_set_id"),
                resultSet.getInt("board_game_id"),
                resultSet.getTimestamp("created_at"),
                resultSet.getTimestamp("updated_at"),
                resultSet.getTimestamp("deleted_at"),
                new ArrayList<>()
        );
    }

    @Override
    protected void insertValidation(BoardGameBox entity) {
        //No database validation for board game boxes
    }

    @Override
    protected void updateValidation(BoardGameBox entity) {
        //No database validation for board game boxes
    }

    @Override
    protected Integer insertImplementation(BoardGameBox entity) {
        final String sql = """
                			INSERT INTO board_game_boxes(title, is_expansion, is_stand_alone, base_set_id, board_game_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        if (entity.getBaseSetId() == null) {
            jdbcTemplate.update(
                    getPreparedStatement(sql, entity),
                    keyHolder
            );
        }
        return (Integer) keyHolder.getKeys().get("id");
    }

    private PreparedStatementCreator getPreparedStatement(String sql, BoardGameBox entity) {
        if (entity.getBaseSetId() == null) {
            return connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, entity.getTitle());
                ps.setBoolean(2, entity.isExpansion());
                ps.setBoolean(3, entity.isStandAlone());
                ps.setNull(4, Types.INTEGER);
                ps.setInt(5, entity.getBoardGameId());
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
                ps.setTimestamp(7, Timestamp.from(Instant.now()));
                return ps;
            };
        } else {
            return connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, entity.getTitle());
                ps.setBoolean(2, entity.isExpansion());
                ps.setBoolean(3, entity.isStandAlone());
                ps.setNull(4, entity.getBaseSetId());
                ps.setInt(5, entity.getBoardGameId());
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
                ps.setTimestamp(7, Timestamp.from(Instant.now()));
                return ps;
            };
        }
    }

    @Override
    protected void updateImplementation(BoardGameBox entity) {
        final String sql = """
                UPDATE board_game_boxes SET title = ?, is_expansion = ?, is_stand_alone = ?, base_set_id = ?, board_game_id = ?, updated_at = ? WHERE id = ?;
                """;
        jdbcTemplate.update(sql, entity.getTitle(), entity.isExpansion(), entity.isStandAlone(), entity.getBaseSetId(), entity.getBoardGameId(), Timestamp.from(Instant.now()), entity.getId());
    }

    public List<SlimBoardGameBox> getSlimBoardGameBoxesByBoardGameId(int boardGameId) {
        String sql = getBaseQueryExcludeDeleted() + " AND board_game_id = ? ";
        List<BoardGameBox> boardGameBoxes = jdbcTemplate.query(
                sql,
                new Object[]{boardGameId},
                new int[]{Types.BIGINT},
                getRowMapper()
        );
        List<SlimBoardGameBox> slimBoardGameBoxes = new ArrayList<>();
        for (BoardGameBox boardGameBox : boardGameBoxes) {
            setCustomFieldsValuesForEntity(boardGameBox);
            slimBoardGameBoxes.add(boardGameBox.convertToSlimBoardGameBox());
        }
        return slimBoardGameBoxes;
    }
}
