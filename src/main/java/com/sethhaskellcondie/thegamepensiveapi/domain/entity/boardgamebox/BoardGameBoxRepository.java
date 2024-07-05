package com.sethhaskellcondie.thegamepensiveapi.domain.entity.boardgamebox;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.EntityRepositoryAbstract;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BoardGameBoxRepository extends EntityRepositoryAbstract<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto>
        implements EntityRepository<BoardGameBox, BoardGameBoxRequestDto, BoardGameBoxResponseDto> {
    protected BoardGameBoxRepository(JdbcTemplate jdbcTemplate) {
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
    public BoardGameBox insert(BoardGameBoxRequestDto requestDto) {
        return null;
    }

    @Override
    public void deleteById(int id) {

    }

    @Override
    protected String getEntityKey() {
        return Keychain.BOARD_GAME_BOX_KEY;
    }

    @Override
    protected RowMapper<BoardGameBox> getRowMapper() {
        return null;
    }

    @Override
    protected void insertValidation(BoardGameBox entity) {

    }

    @Override
    protected void updateValidation(BoardGameBox entity) {

    }

    @Override
    protected Integer insertImplementation(BoardGameBox entity) {
        return null;
    }

    @Override
    protected void updateImplementation(BoardGameBox entity) {

    }
}
