package com.sethhaskellcondie.thegamepensiveapi.system;

public interface System {
	//in every Entity
	String getBaseQuery();
	SystemDto getDto();

	String getName();
	void setName(String name);
	int getGeneration();
	void setGeneration(int generation);
	boolean isHandheld();
	void setHandheld(boolean handheld);
}
