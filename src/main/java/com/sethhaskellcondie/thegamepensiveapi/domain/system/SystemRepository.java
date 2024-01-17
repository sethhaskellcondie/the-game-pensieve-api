package com.sethhaskellcondie.thegamepensiveapi.domain.system;

import com.sethhaskellcondie.thegamepensiveapi.domain.EntityRepository;

/**
 * Each repository that extends the EntityRepository will enforce the basic
 * CRUD functions for interacting with the database with the proper names,
 * signatures, and exceptions to be thrown. Then additional functions can
 * be included as needed.
 */
public interface SystemRepository extends EntityRepository<System> {
}
