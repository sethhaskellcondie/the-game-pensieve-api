package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.sethhaskellcondie.thegamepensiveapi.exceptions.ExceptionFailedDbValidation;

public class SystemRepositoryImpl implements SystemRepository {
	private final JdbcTemplate jdbcTemplate;
	private String getBaseQuery() {
		// include a WHERE 1 = 1 clause at the end, so we can always append with AND
		return "SELECT * FROM systems WHERE 1 = 1 ";
	}

	public SystemRepositoryImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public System insert(System system) throws ExceptionFailedDbValidation {
		systemDbValidation(system);
		//TODO refactor to use Prepared Statement
		String sql = """
   			INSERT INTO systems(name, generation, handheld) VALUES (?, ?, ?);
			""";
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			preparedStatement.setString(1, system.getName());
			preparedStatement.setInt(2, system.getGeneration());
			preparedStatement.setBoolean(3, system.isHandheld());
			return preparedStatement;
		}, keyHolder);

		return getById((Integer) keyHolder.getKey());
	}

	@Override
	public List<System> getWithFilters(String filters) {
		String sql = getBaseQuery() + filters + ";";
		//TODO refactor to use Prepared Statement
		List<System> results = jdbcTemplate.query(
			sql,
			(resultSet, i) ->
			new System(
				resultSet.getInt("id"),
				resultSet.getString("name"),
				resultSet.getInt("generation"),
				resultSet.getBoolean("handheld")
			));
		return results;
	}

	@Override
	public System getById(int id) {
		String sql = getBaseQuery() + " AND id = ? ;";
		//TODO refactor to use Prepared Statement
		List<System> results = jdbcTemplate.query(
			sql,
			(resultSet, i) ->
				new System(
					resultSet.getInt("id"),
					resultSet.getString("name"),
					resultSet.getInt("generation"),
					resultSet.getBoolean("handheld")
				),
			id
		);
		if (results.isEmpty()) {
			return null;
		}
		return results.get(0);
	}

	@Override
	public System update(System system) throws ExceptionFailedDbValidation {
		systemDbValidation(system);
		String sql = """
   			UPDATE systems SET name = ?, generation = ?, handheld = ?;
			""";
		jdbcTemplate.update(connection -> {
			PreparedStatement preparedStatement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			preparedStatement.setString(1, system.getName());
			preparedStatement.setInt(2, system.getGeneration());
			preparedStatement.setBoolean(3, system.isHandheld());
			return preparedStatement;
		});

		return getById(system.getId());
	}

	@Override
	public void deleteById(int id) throws ExceptionFailedDbValidation {
		//TODO refactor to use Prepared Statement
		String sql = """
   			DELETE FROM systems WHERE id = ?;
			""";
		int rowsUpdated = jdbcTemplate.update(sql, id);
		if (rowsUpdated < 1) {
			throw new ExceptionFailedDbValidation("System delete failed, id not found.");
		}
		if (rowsUpdated > 1) {
			//TODO include a logger and log a database anomaly.
		}
	}

	private void systemDbValidation(System system) throws ExceptionFailedDbValidation {
		List<System> existingSystems = getWithFilters(" AND name = " + system.getName());
		if (!existingSystems.isEmpty()) {
			throw new ExceptionFailedDbValidation("System write failed, duplicate name found.");
		}
	}
}
