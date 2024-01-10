package com.sethhaskellcondie.thegamepensiveapi.controllers;

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
public class SystemController {
	private final JdbcTemplate jdbcTemplate;

	public SystemController(JdbcTemplate jdbcTemplate)
	{
		this.jdbcTemplate = jdbcTemplate;
	}

	//TODO update this cross origins configuration to be at the controller or global level
	@CrossOrigin(origins = "http://localhost:4200")
	@GetMapping("")
	public List<SystemResponseDto> getAllSystems() {
		return null;
	}

	@GetMapping("/{id}")
	public SystemResponseDto getOneSystem(@PathVariable int id) {
		return null;
	}

	@PostMapping("")
	public SystemResponseDto createNewSystem(@RequestBody SystemRequestDto newSystem) {
		return null;
	}

	@PutMapping("/{id}")
	public SystemResponseDto updateExistingSystem(@PathVariable int id, @RequestBody SystemRequestDto updateSystem) {
		return null;
	}

	@DeleteMapping("/{id}")
	public void deleteExistingSystem(@PathVariable int id) {
	}

	public record SystemRequestDto(String name, int generation, boolean handheld) {};
	public record SystemResponseDto(int id, String name, int generation, boolean handheld) {};
}
