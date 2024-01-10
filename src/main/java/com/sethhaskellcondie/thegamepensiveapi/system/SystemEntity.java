package com.sethhaskellcondie.thegamepensiveapi.system;

import com.sethhaskellcondie.thegamepensiveapi.Entity;

public class SystemEntity extends Entity implements System {
	private String name;
	private int generation;
	private boolean handheld;

	public SystemEntity() {
		super();
	}

	public SystemEntity(Integer id) {
		super(id);
	}

	public SystemEntity(String name, int generation, boolean handheld) {
		super();
		this.name = name;
		this.generation = generation;
		this.handheld = handheld;
	}

	public SystemEntity(Integer id, String name, int generation, boolean handheld) {
		super(id);
		this.name = name;
		this.generation = generation;
		this.handheld = handheld;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getGeneration() {
		return generation;
	}

	public void setGeneration(int generation) {
		this.generation = generation;
	}

	public boolean isHandheld() {
		return handheld;
	}

	public void setHandheld(boolean handheld) {
		this.handheld = handheld;
	}

	// -------- I'm hoping everything above here can be generated into a base class derived from the database table --------

	// include a WHERE 1 = 1 clause at the end, so we can always append with AND
	public String getBaseQuery() {
		return "SELECT * FROM systems WHERE 1 = 1";
	}

	public SystemDto getDto() {
		return new SystemDto(this.id, this.name, this.generation, this.handheld);
	}
}
