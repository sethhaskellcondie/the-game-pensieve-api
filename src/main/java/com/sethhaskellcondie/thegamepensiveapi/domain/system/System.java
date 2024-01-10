package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.Entity;

public class System extends Entity
{
	//inherit Id from Entity
	private String name;
	private int generation;
	private boolean handheld;

	record SystemDto(Integer id, String name, int generation, boolean handheld) { }

	public System() {
		super();
	}

	//only used in repositories and tests
	public System(Integer id, String name, int generation, boolean handheld) {
		super(id);
		this.name = name;
		this.generation = generation;
		this.handheld = handheld;
	}

	public String getName()
	{
		return name;
	}

	public int getGeneration()
	{
		return generation;
	}

	public boolean isHandheld()
	{
		return handheld;
	}

	public SystemDto getDto() {
		return new SystemDto(this.id, this.name, this.generation, this.handheld);
	}

	public void update(String name, int generation, boolean handheld) {
		this.name = name;
		this.generation = generation;
		this.handheld = handheld;
	}
}
