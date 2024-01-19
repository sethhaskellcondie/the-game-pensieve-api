package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ErrorLogs;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;
import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionResourceNotFound;

@Repository
public class SystemRepositoryImpl implements SystemRepository {
	private final JdbcTemplate jdbcTemplate;

	// include a WHERE 1 = 1 clause at the end, so we can always append with AND

	private final String baseQuery = "SELECT * FROM systems WHERE 1 = 1 ";
	//the rowMapper is used to convert the data from the table into object that can be used in this system

	private final RowMapper<System> rowMapper =
		(resultSet, i) ->
			new System(
				resultSet.getInt("id"),
				resultSet.getString("name"),
				resultSet.getInt("generation"),
				resultSet.getBoolean("handheld")
			);
	private final Logger logger = LoggerFactory.getLogger(SystemRepositoryImpl.class);

	public SystemRepositoryImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public System hydrateFromRequestDto(SystemRequestDto requestDto) {
		System newSystem = new System();
		return newSystem.updateFromRequest(requestDto);
	}

	@Override
	public System insert(System system) throws ExceptionFailedDbValidation {
		//to change this into an upsert, if the system "isPersistent()" then return update();

		systemDbValidation(system);

		String sql = """
   			INSERT INTO systems(name, generation, handheld) VALUES (?, ?, ?);
			""";
		KeyHolder keyHolder = new GeneratedKeyHolder();
		/**
		 * This update call will take a preparedStatementCreator and a KeyHolder,
		 * the preparedStatementCreator takes a connection,and the connection can
		 * include a Statement to hold the generated key and then put them in the
		 * KeyHolder.
		 */
		jdbcTemplate.update(
			connection -> {
				PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				ps.setString(1, system.getName());
				ps.setInt(2, system.getGeneration());
				ps.setBoolean(3, system.isHandheld());
				return ps;
			},
			keyHolder
		);
		Integer generatedId = (Integer) keyHolder.getKey();

		try {
			return getById(generatedId);
		} catch (ExceptionResourceNotFound | NullPointerException e) {
			// we shouldn't ever reach this block of code because the database is managing the ids
			// but if we do then we better log it
			logger.error(ErrorLogs.InsertThenRetrieveError(system.getClass().getSimpleName(), generatedId));
			return null;
		}
	}

	@Override
	public List<System> getWithFilters(String filters) {
		String sql = baseQuery + filters + ";";
		return jdbcTemplate.query(sql, rowMapper);
	}

	@Override
	public System getById(int id) throws ExceptionResourceNotFound {
		String sql = baseQuery + " AND id = ? ;";
		System system = jdbcTemplate.queryForObject(sql, rowMapper);
		if (system == null || !system.isPersistent()) {
			throw new ExceptionResourceNotFound("System not found in the database with id: " + id);
		}
		return system;
	}

	@Override
	public System update(System system) throws ExceptionFailedDbValidation {
		//to change this into an upsert: if (!system.isPersistent()) { return insert() }

		systemDbValidation(system);
		String sql = """
   			UPDATE systems SET name = ?, generation = ?, handheld = ? WHERE id = ?;
			""";
		jdbcTemplate.update(
			sql,
			system.getName(),
			system.getGeneration(),
			system.isHandheld(),
			system.getId()
		);

		try {
			return getById(system.getId());
		} catch (ExceptionResourceNotFound e) {
			// we shouldn't ever reach this block of code because the database is managing the ids
			// but if we do then we better log it
			logger.error(ErrorLogs.UpdateThenRetrieveError(system.getClass().getSimpleName(), system.getId()));
			return null;
		}
	}

	@Override
	public void deleteById(int id) throws ExceptionResourceNotFound
	{
		String sql = """
   			DELETE FROM systems WHERE id = ?;
			""";
		int rowsUpdated = jdbcTemplate.update(sql, id);
		if (rowsUpdated < 1) {
			throw new ExceptionResourceNotFound("System delete failed, system with id " + id + " not found.");
		}
	}

	private void systemDbValidation(System system) throws ExceptionFailedDbValidation {
		List<System> existingSystems = getWithFilters(" AND name = " + system.getName());
		if (!existingSystems.isEmpty()) {
			throw new ExceptionFailedDbValidation("System write failed, duplicate name found.");
		}
	}
}
