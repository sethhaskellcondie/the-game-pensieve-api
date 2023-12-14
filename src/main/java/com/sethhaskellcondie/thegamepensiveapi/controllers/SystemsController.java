package com.sethhaskellcondie.thegamepensiveapi.controllers;

//right now I'm going to shortcut the domain process and have the controller handle everything from the request validation to the persistence
//TODO create entities for the objects
//TODO create repositories for persistence
//TODO create interfaces for these classes update them to be implementations

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("systems")
public class SystemsController
{
	private final JdbcTemplate jdbcTemplate;

	public SystemsController(JdbcTemplate jdbcTemplate)
	{
		this.jdbcTemplate = jdbcTemplate;
	}

	//TODO update this cross origins configuration to be at the controller or global level
	@CrossOrigin(origins = "http://localhost:4200")
	@GetMapping("")
	public List<SystemResponseDto> getAllSystems() {
		String sql = """
   			SELECT * FROM systems
			""";
		List<SystemResponseDto> results = jdbcTemplate.query(sql, (resultSet, i) ->
			new SystemResponseDto(
				resultSet.getInt("id"),
				resultSet.getString("name"),
				resultSet.getInt("generation"),
				resultSet.getBoolean("handheld")
			));
		return results;
	}

	@GetMapping("/{id}")
	public SystemResponseDto getOneSystem(@PathVariable int id) {
		//TODO update this to validate that there is only one entry with that id in the database
		String sql = """
   			SELECT * FROM systems WHERE id = ?
			""";
		List<SystemResponseDto> results = jdbcTemplate.query(
			sql,
			(resultSet, i) ->
			new SystemResponseDto(
				resultSet.getInt("id"),
				resultSet.getString("name"),
				resultSet.getInt("generation"),
				resultSet.getBoolean("handheld")
			),
			id
		);
		return results.get(0);
	}

	@PostMapping("")
	public SystemResponseDto createNewSystem(@RequestBody SystemRequestDto newSystem) {
		//check and see if there are existing systems with the same name, generation, and handheld
		List<SystemResponseDto> existingEntries = getAllByNameGenerationHandheld(newSystem.name, newSystem.generation, newSystem.handheld);
		List<Integer> existingIds = new ArrayList<Integer>();
		for (SystemResponseDto entry : existingEntries) {
			existingIds.add(entry.id);
		}
		//insert the new system
		String sql = """
   			INSERT INTO systems(name, generation, handheld) VALUES (?, ?, ?);
			""";
		jdbcTemplate.update(sql, newSystem.name, newSystem.generation, newSystem.handheld);
		//get the correct id and return the object
		List<SystemResponseDto> allEntries = getAllByNameGenerationHandheld(newSystem.name, newSystem.generation, newSystem.handheld);
		for (SystemResponseDto entry: allEntries) {
			if (!existingIds.contains(entry.id)) {
				return entry;
			}
		}
		//TODO return an error if the new entry isn't found in the previous loop
		return null;
	}

	private List<SystemResponseDto> getAllByNameGenerationHandheld(String name, int generation, boolean handheld) {
		String sql = """
   			SELECT * FROM systems WHERE name = ? AND generation = ? AND handheld = ?
			""";
		List<SystemResponseDto> results = jdbcTemplate.query(sql,
			(resultSet, i) ->
				new SystemResponseDto(
					resultSet.getInt("id"),
					resultSet.getString("name"),
					resultSet.getInt("generation"),
					resultSet.getBoolean("handheld")
				),
			name, generation, handheld
		);
		return results;
	}

	@PutMapping("/{id}")
	public SystemResponseDto updateExistingSystem(@PathVariable int id, @RequestBody SystemRequestDto updateSystem) {
		//TODO validate the id
		String sql = """
   			UPDATE systems SET name = ?, generation = ?, handheld = ? WHERE id = ?;
			""";
		int rowsUpdated = jdbcTemplate.update(
			sql, updateSystem.name, updateSystem.generation, updateSystem.handheld, id
		);
		if (rowsUpdated > 1) {
			//TODO update the error handling to be better
		}
		return getOneSystem(id);
	}

	@DeleteMapping("/{id}")
	public void deleteExistingSystem(@PathVariable int id) {
		//TODO validate the id
		String sql = """
   			DELETE FROM systems WHERE id = ?;
			""";
		int rowsUpdated = jdbcTemplate.update(sql, id);
		if (rowsUpdated != 1) {
			//TODO update the error handling
		}
	}

	public record SystemRequestDto(String name, int generation, boolean handheld) {};
	public record SystemResponseDto(int id, String name, int generation, boolean handheld) {};
}
