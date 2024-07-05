package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgame;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BoardGameRepository extends EntityRepositoryAbstract<BoardGame, BoardGameRequestDto, BoardGameResponseDto>
        implements EntityRepository<BoardGame, BoardGameRequestDto, BoardGameResponseDto> {
    protected BoardGameRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    protected String getBaseQuery() {
        return null;
    }

    @Override
    protected String getBaseQueryJoinCustomFieldValues() {
        return null;
    }

    @Override
    protected String getBaseQueryWhereDeletedAtIsNotNull() {
        return null;
    }

    @Override
    protected String getBaseQueryIncludeDeleted() {
        return null;
    }

    @Override
    public BoardGame insert(BoardGameRequestDto requestDto) {
        return null;
    }

    @Override
    public void deleteById(int id) {

    }

    @Override
    protected String getEntityKey() {
        return Keychain.BOARD_GAME_KEY;
    }

    @Override
    protected RowMapper<BoardGame> getRowMapper() {
        return null;
    }

    @Override
    protected void insertValidation(BoardGame entity) {

    }

    @Override
    protected void updateValidation(BoardGame entity) {

    }

    @Override
    protected Integer insertImplementation(BoardGame entity) {
        return null;
    }

    @Override
    protected void updateImplementation(BoardGame entity) {

    }
}
