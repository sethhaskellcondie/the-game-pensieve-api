package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import org.springframework.stereotype.Service;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;
import com.sethhaskellcondie.thegamepensiveapi.domain.EntityServiceImpl;

@Service
public class SystemServiceImpl extends EntityServiceImpl<System> implements SystemService {

	public SystemServiceImpl(EntityRepository<System> repository)
	{
		super(repository);
	}
}
