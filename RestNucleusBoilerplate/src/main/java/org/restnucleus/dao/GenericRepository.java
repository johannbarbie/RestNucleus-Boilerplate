package org.restnucleus.dao;

import java.util.Collection;
import java.util.List;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.identity.LongIdentity;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.restnucleus.PersistenceConfiguration;


/**
 * A data access object offering most common CRUD operations, queries and paging on JDO.
 * 
 * with ideas from here: http://vikinghammer.com/2011/02/09/simplified-dao-helper-for-using-jdo-with-google-appengine/
 * 
 * @author johba
 */

public class GenericRepository {
	public static final String OBJECT_QUERY_PARAM = "objectQuery";

	protected PersistenceManagerFactory pmf = null;
	protected PersistenceManager pm = null;

	// for testing with mock objects
	public GenericRepository(PersistenceManagerFactory pmf) {
		this.pmf = pmf;
	}

	public GenericRepository() {
		this.pmf = PersistenceConfiguration.getInstance()
				.getEntityManagerFactory();
	}

	/*
	 * get a persistencemanager to use the datastore
	 */
	public void getPersistenceManager() {
		if (null == pm || pm.isClosed()) {
			pm = pmf.getPersistenceManager();
		}
	}
	
	public void flush(){
		getPersistenceManager();
		pm.flush();
	}

	/*
	 * this has to be called after none of the handled objects are required any
	 * more.
	 */
	public void closePersistenceManager() {
		if (null != pm && !pm.isClosed()) {
			pm.flush();
			pm.close();
		}
	}

	public <K extends Model> K detach(K k, Class<K> entityClass) {
		getPersistenceManager();
		return pm.detachCopy(k);
	}

	public Model detach(Model m) {
		getPersistenceManager();
		return pm.detachCopy(m);
	}

	public <K extends Model> Collection<K> detach(Collection<K> k, Class<K> entityClass) {
		getPersistenceManager();
		return pm.detachCopyAll(k);
	}

	private void handleException(Exception e) {
		closePersistenceManager();
		e.printStackTrace();
		throw new WebApplicationException(e.getMessage(),Response.Status.INTERNAL_SERVER_ERROR);
	}

	/*
	 * #############################
	 * 
	 * CRUD Opeations
	 */
	public void add(Model entity) {
		if (null != entity.getId())
			throw new WebApplicationException(
					"object contains id already!",
					Response.Status.CONFLICT);
		getPersistenceManager();
		try {
			pm.makePersistent(entity);
		} catch (Exception e) {
			handleException(e);
		}
	}
	
	public <K extends Model> boolean existsObject(Long id, Class<K> entityClass) {
		K rv = getObjectById(id, false, entityClass);
		return (null!=rv);
	}
	
	public <K extends Model> K getObjectById(Long id, Class<K> entityClass) {
		return getObjectById(id, true, entityClass);
	}

	@SuppressWarnings("unchecked")
	private <K extends Model> K getObjectById(Long id, boolean validate, Class<K> entityClass) {
		getPersistenceManager();
		if (null == id)
			throw new WebApplicationException("id == null",Response.Status.BAD_REQUEST);
		try {
			return (K) pm.getObjectById(new LongIdentity(entityClass, id),validate);
		}catch (JDOObjectNotFoundException e1){
			closePersistenceManager();
			throw new WebApplicationException(
					"No entity found with id: "+id, 
					Response.Status.NOT_FOUND);
		}catch (Exception e) {
			handleException(e);
		}
		return null;
	}

	public <K extends Model> void update(K entity, Class<K> entityClass) {
		K rv = getObjectById(entity.getId(), entityClass);
		rv.update(entity);
	}

	public <K extends Model> void delete(Long id, Class<K> entityClass) {
		getPersistenceManager();
		try {
			pm.deletePersistent(pm.getObjectById(new LongIdentity(entityClass, id)));
		} catch (Exception e) {
			handleException(e);
		}
	}

	public <K extends Model> K queryEntity(RNQuery q, Class<K> entityClass){
		return queryEntity(q, entityClass, true);
	}
	
	@SuppressWarnings("unchecked")
	public <K extends Model> K queryEntity(RNQuery q, Class<K> entityClass, boolean validate){
		if (null==q)
			throw new WebApplicationException(
					"no query provided", 
					Response.Status.NOT_FOUND);
		getPersistenceManager();
		Query query = q.getJdoQ(pm, entityClass);
		query.setUnique(true);
		K result = null;
		try {
			if (q.getQueryObjects().size()==0){
				result = (K) query.execute();
			}else{
				result = (K) query.executeWithMap(q.getQueryObjects());
			}
		} catch (Exception e) {
			handleException(e);
		} 
		if (validate && null==result)
			throw new WebApplicationException("No entity found for this query", 
					Response.Status.NOT_FOUND);
		return result;
	}

	@SuppressWarnings("unchecked")
	public <K extends Model> List<K> queryList(RNQuery q, Class<K> entityClass){
		if (null==q)
			q = new RNQuery();
		getPersistenceManager();
		Query query = q.getJdoQ(pm, entityClass);
		try {
			
			List<K> results = null;
			if (q.getQueryObjects().size()==0){
				results = (List<K>) query.execute();
			}else{
				results = (List<K>) query.executeWithMap(q.getQueryObjects());
			}
			return results;
		} catch (Exception e) {
			handleException(e);
		} 
		return null;
	}
	
	public <K extends Model> void queryDelete(RNQuery q, Class<K> entityClass){
		if (null==q)
			throw new WebApplicationException("no query provided",Response.Status.NOT_FOUND);
		getPersistenceManager();
		Query query = q.getJdoQ(pm, entityClass);
		//a range does not work with a delete query
		query.setRange(null);
		try {
			if (null==q || q.getQueryObjects().size()==0){
				query.deletePersistentAll();
			}else{
				query.deletePersistentAll(q.getQueryObjects());
			}
		} catch (Exception e) {
			handleException(e);
		} 
	}
	
	public <K extends Model> Long count(RNQuery q, Class<K> entityClass){
		getPersistenceManager();
		Query query = null;
		if (null!=q){
			query = q.getJdoQ(pm, entityClass);
			//a range query with a count makes little sense to me
			query.setRange(null);
		}else
			query = pm.newQuery(entityClass);
		query.setResult("count(id)");
		Long rv = null;
		try {
			if (null==q || q.getQueryObjects().size()==0){
				rv = (Long) query.execute();
			}else{
				rv = (Long) query.executeWithMap(q.getQueryObjects());
			}
		} finally {
			query.closeAll();
		}
		return rv;
	}
}
