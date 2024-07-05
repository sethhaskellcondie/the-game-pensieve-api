package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import com.sethhaskellcondie.thegamepensiveapi.domain.exceptions.ExceptionResourceNotFound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

@Repository
public class BoardGameRepository extends EntityRepositoryAbstract<BoardGame, BoardGameRequestDto, BoardGameResponseDto>
        implements EntityRepository<BoardGame, BoardGameRequestDto, BoardGameResponseDto> {
    protected BoardGameRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String getBaseQuery() {
        return """
                SELECT board_games.id, board_games.title, board_games.created_at, board_games.updated_at, board.games.deleted_at
                FROM board_games WHERE board_games.deleted_at IS NULL
                """;
    }

    @Override
    protected String getBaseQueryJoinCustomFieldValues() {
        return """
                SELECT board_games.id, board_games.title, board_games.created_at, board_games.updated_at, board.games.deleted_at
                FROM board_games
                JOIN custom_field_values as values ON board_games.id = values.entity_id
                	  	JOIN custom_fields as fields ON values.custom_field_id = fields.id
                               	WHERE board_games.deleted_at IS NULL
                                 AND values.entity_key = 'board_game'
                """;
    }

    @Override
    protected String getBaseQueryWhereDeletedAtIsNotNull() {
        return """
                SELECT board_games.id, board_games.title, board_games.created_at, board_games.updated_at, board.games.deleted_at
                FROM board_games WHERE board_games.deleted_at IS NOT NULL
                """;
    }

    @Override
    protected String getBaseQueryIncludeDeleted() {
        return """
                SELECT board_games.id, board_games.title, board_games.created_at, board_games.updated_at, board.games.deleted_at
                FROM board_games WHERE 1 = 1
                """;
    }

    @Override
    public BoardGame insert(BoardGameRequestDto requestDto) {
        final BoardGame boardGame = new BoardGame().updateFromRequestDto(requestDto);
        return this.insert(boardGame);
    }

    @Override
    public void deleteById(int id) {
        final String sql = """
                			UPDATE board_games SET deleted_at = ? WHERE id = ?;
                """;
        int rowsUpdated = jdbcTemplate.update(sql, Timestamp.from(Instant.now()), id);
        if (rowsUpdated < 1) {
            throw new ExceptionResourceNotFound("Delete failed", getEntityKey(), id);
        }
    }

    @Override
    protected String getEntityKey() {
        return Keychain.BOARD_GAME_KEY;
    }

    @Override
    protected RowMapper<BoardGame> getRowMapper() {
        return (resultSet, rowNumber) -> new BoardGame(
                resultSet.getInt("id"),
                resultSet.getString("title"),
                resultSet.getTimestamp("created_at"),
                resultSet.getTimestamp("updated_at"),
                resultSet.getTimestamp("deleted_at"),
                new ArrayList<>()
        );
    }

    @Override
    protected void insertValidation(BoardGame entity) {
        //No database validation for board games
    }

    @Override
    protected void updateValidation(BoardGame entity) {
        //No database validation for board games
    }

    @Override
    protected Integer insertImplementation(BoardGame entity) {
        final String sql = """
                			INSERT INTO board_games(title, created_at, updated_at) VALUES (?, ?, ?);
                """;
        final KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                connection -> {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, entity.getTitle());
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                    ps.setTimestamp(3, Timestamp.from(Instant.now()));
                    return ps;
                },
                keyHolder
        );
        return (Integer) keyHolder.getKeys().get("id");
    }

    @Override
    protected void updateImplementation(BoardGame entity) {
        final String sql = """
                UPDATE board_games SET title = ?, updated_at = ? WHERE id = ?;
                """;
        jdbcTemplate.update(sql, entity.getTitle(), Timestamp.from(Instant.now()), entity.getId());
    }
}
