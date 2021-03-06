package fr.jeci.alfresco.saod.sql;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import fr.jeci.alfresco.saod.SaodException;
import fr.jeci.alfresco.saod.pojo.NodeStat;
import fr.jeci.alfresco.saod.pojo.PrintNode;

@Component
public class LocalDaoImpl implements LocalDao {
	static final Logger LOG = LoggerFactory.getLogger(LocalDaoImpl.class);

	/* SQL parameters */
	private static final String SUM_SIZE = "sum_size";
	private static final String SUM_ELEMENTS = "sum_elements";
	private static final String PARENT_NODE_ID = "parent_node_id";
	private static final String LOCAL_SIZE = "local_size";
	private static final String LOCAL_ELEMENTS = "local_elements";
	private static final String NODE_ID = "node_id";
	private static final String NODE_TYPE = "node_type";

	/* Type of node */
	// private static final Integer TYPE_FILE = 0;

	private static final Integer TYPE_DIRECTORY = 1;

	private JdbcTemplate jdbcTemplate;

	@Autowired
	public void setDataSource(@Qualifier("localDataSource") DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
		this.jdbcTemplate.setFetchSize(FETCH_SIZE);
	}

	@Autowired
	@Qualifier("localSqlQueries")
	private SqlQueries sqlQueries;

	@Override
	public void ping() throws SaodException {
		LOG.info("Ping Local HSQL Database");
		String query = sqlQueries.getQuery("select_ping.sql");
		this.jdbcTemplate.execute(query);
	}

	@Override
	public void startRun() throws SaodException {
		String query = sqlQueries.getQuery("start_run.sql");
		this.jdbcTemplate.execute(query);
	}

	@Override
	public void stopRun() throws SaodException {
		String query = sqlQueries.getQuery("stop_run.sql");
		this.jdbcTemplate.execute(query);
	}

	@Override
	public Timestamp getRun() throws SaodException {
		String query = sqlQueries.getQuery("has_run.sql");
		SqlRowSet queryForRowSet = this.jdbcTemplate.queryForRowSet(query);
		if (queryForRowSet.first()) {
			return queryForRowSet.getTimestamp(1);
		} else {
			return null;
		}
	}

	@Override
	public Timestamp getLastSuccess() throws SaodException {
		String query = sqlQueries.getQuery("last_success.sql");
		SqlRowSet queryForRowSet = this.jdbcTemplate.queryForRowSet(query);
		if (queryForRowSet.first()) {
			return queryForRowSet.getTimestamp(1);
		} else {
			return null;
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void resetDatabase() throws SaodException {
		LOG.info("Purge Database");
		this.jdbcTemplate.execute(sqlQueries.getQuery("delete_all_data.sql"));
		if (LOG.isDebugEnabled()) {
			Long count = this.jdbcTemplate.queryForObject("select count(*) from STATS_DIR_LOCAL_SIZE", Long.class);
			LOG.debug("Row count = {}", count);
		}
	}

	@Override
	@Transactional
	public void checkpoint() {
		this.jdbcTemplate.execute("CHECKPOINT DEFRAG");
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void insertStatsDirLocalSize(Map<Long, NodeStat> dirLocalSize) throws SaodException {

		List<MapSqlParameterSource> batchArgs = new ArrayList<>(FETCH_SIZE);
		for (Entry<Long, NodeStat> e : dirLocalSize.entrySet()) {
			NodeStat stat = e.getValue();
			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue(NODE_ID, e.getKey());
			parameters.addValue(LOCAL_SIZE, stat.getSize());
			parameters.addValue(NODE_TYPE, TYPE_DIRECTORY);
			parameters.addValue(LOCAL_ELEMENTS, stat.getNumberElements());
			batchArgs.add(parameters);

			if (batchArgs.size() >= FETCH_SIZE) {
				try {
					long start = System.currentTimeMillis();
					insertStatsDirLocalSize(batchArgs);
					LOG.debug("insertStatsDirLocalSize Batch : {} ms", (System.currentTimeMillis() - start));

				} catch (org.springframework.dao.DuplicateKeyException edk) {
					LOG.warn(edk.getMessage() + " retry without batch ");
					long start = System.currentTimeMillis();
					insertStatsDirLocalSizeNoBatch(batchArgs);
					LOG.debug("insertStatsDirLocalSize NO-batch : {} ms", (System.currentTimeMillis() - start));
				}
				batchArgs.clear();
			}
		}

		if (batchArgs.size() > 0) {
			insertStatsDirLocalSize(batchArgs);
		}
	}

	@Transactional(propagation = Propagation.REQUIRED)
	private void insertStatsDirLocalSize(List<MapSqlParameterSource> batchArgs) throws SaodException {
		final NamedParameterJdbcTemplate jdbcNamesTpl = new NamedParameterJdbcTemplate(this.jdbcTemplate);
		final String query = sqlQueries.getQuery("insert_stats_dir_local_size.sql");
		jdbcNamesTpl.batchUpdate(query, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	private void insertStatsDirLocalSizeNoBatch(List<MapSqlParameterSource> batchArgs) throws SaodException {
		final NamedParameterJdbcTemplate jdbcNamesTpl = new NamedParameterJdbcTemplate(this.jdbcTemplate);
		final String query = sqlQueries.getQuery("insert_stats_dir_local_size.sql");
		for (MapSqlParameterSource paramSource : batchArgs) {
			try {
				jdbcNamesTpl.update(query, paramSource);
			} catch (org.springframework.dao.DuplicateKeyException edk) {
				LOG.error("Duplicate Entry for nodeid=" + paramSource.getValue(NODE_ID) + " - skip", edk);
			}
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void insertStatsDirNoSize(List<Long> parentsid) throws SaodException {
		final NamedParameterJdbcTemplate jdbcNamesTpl = new NamedParameterJdbcTemplate(this.jdbcTemplate);
		final String query = sqlQueries.getQuery("insert_stats_dir_local_size.sql").replace("insert into",
				"INSERT IGNORE INTO");

		List<MapSqlParameterSource> batchArgs = new ArrayList<>(FETCH_SIZE);

		for (Long id : parentsid) {
			if (loadRow(id) != null) {
				continue;
			}

			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue(NODE_ID, id);
			parameters.addValue(LOCAL_SIZE, 0);
			parameters.addValue(NODE_TYPE, TYPE_DIRECTORY);
			parameters.addValue(LOCAL_ELEMENTS, 0);
			batchArgs.add(parameters);
			if (batchArgs.size() >= FETCH_SIZE) {
				jdbcNamesTpl.batchUpdate(query, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
				batchArgs.clear();
			}
		}

		if (batchArgs.size() > 0) {
			jdbcNamesTpl.batchUpdate(query, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public List<Long> selectLeafNode() throws SaodException {
		String query = sqlQueries.getQuery("select_leaf_node.sql");
		return this.jdbcTemplate.queryForList(query, Long.class);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void upadteStatsDatabaseZero(List<Long> parentsid) throws SaodException {
		final NamedParameterJdbcTemplate jdbcNamesTpl = new NamedParameterJdbcTemplate(this.jdbcTemplate);
		final String dirsumSizeQuery = sqlQueries.getQuery("update_stats_dir_sum_size.sql");
		final String numbersumElementsQuery = sqlQueries.getQuery("update_node_number_sum_elements.sql");

		List<MapSqlParameterSource> batchArgs = new ArrayList<>(FETCH_SIZE);

		for (Long id : parentsid) {
			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue(NODE_ID, id);
			parameters.addValue(SUM_SIZE, 0);
			parameters.addValue(SUM_ELEMENTS, 0);

			batchArgs.add(parameters);

			if (batchArgs.size() >= FETCH_SIZE) {
				jdbcNamesTpl.batchUpdate(dirsumSizeQuery, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
				jdbcNamesTpl.batchUpdate(numbersumElementsQuery,
						batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
				batchArgs.clear();
			}
		}
		if (batchArgs.size() > 0) {
			jdbcNamesTpl.batchUpdate(dirsumSizeQuery, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
			jdbcNamesTpl.batchUpdate(numbersumElementsQuery,
					batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void resetStatsDatabase() throws SaodException {
		String dirsumSizeQuery = sqlQueries.getQuery("update_reset_dir_sum_size.sql");
		this.jdbcTemplate.update(dirsumSizeQuery);
		String numberElementsQuery = sqlQueries.getQuery("update_reset_number_elements.sql");
		this.jdbcTemplate.update(numberElementsQuery);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void upadteStatsDatabase(List<Long> nodes) throws SaodException {
		String dirsumSizeQuery = sqlQueries.getQuery("update_dir_sum_size.sql");
		String numberElementsQuery = sqlQueries.getQuery("update_node_number_elements.sql");
		String nodeTypeQuery = sqlQueries.getQuery("update_file_node_type.sql");
		/**
		 * TODO use prep stmt, we cant't use batchUpdate here
		 */
		for (Long id : nodes) {
			this.jdbcTemplate.update(dirsumSizeQuery, id);
			this.jdbcTemplate.update(numberElementsQuery, id);
		}
		this.jdbcTemplate.update(nodeTypeQuery);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public void updateParentNodeId(Map<Long, Long> nodeids) throws SaodException {
		final NamedParameterJdbcTemplate jdbcNamesTpl = new NamedParameterJdbcTemplate(this.jdbcTemplate);
		final String query = sqlQueries.getQuery("update_parent_node_id.sql");

		List<MapSqlParameterSource> batchArgs = new ArrayList<>(FETCH_SIZE);

		for (Entry<Long, Long> e : nodeids.entrySet()) {
			MapSqlParameterSource parameters = new MapSqlParameterSource();
			parameters.addValue(NODE_ID, e.getKey());
			parameters.addValue(PARENT_NODE_ID, e.getValue());
			batchArgs.add(parameters);

			if (batchArgs.size() >= FETCH_SIZE) {
				jdbcNamesTpl.batchUpdate(query, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
				batchArgs.clear();
			}
		}

		if (batchArgs.size() > 0) {
			jdbcNamesTpl.batchUpdate(query, batchArgs.toArray(new MapSqlParameterSource[batchArgs.size()]));
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public List<Long> selectRootFolders() throws SaodException {
		String query = sqlQueries.getQuery("select_root_folders.sql");
		final SqlRowSet queryForRowSet = this.jdbcTemplate.queryForRowSet(query);

		final List<Long> ids = new ArrayList<>();
		while (queryForRowSet.next()) {
			ids.add(queryForRowSet.getLong(1));
		}

		return ids;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public List<Long> selectSubFolders(Long nodeid) throws SaodException {
		String query = sqlQueries.getQuery("select_sub_folders.sql");
		return this.jdbcTemplate.queryForList(query, Long.class, nodeid);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public List<Long> selectparentFolders(List<Long> nodesid) throws SaodException {
		if (nodesid == null || nodesid.isEmpty()) {
			return Collections.emptyList();
		}

		NamedParameterJdbcTemplate jdbcNamesTpl = new NamedParameterJdbcTemplate(this.jdbcTemplate);

		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("ids", nodesid);

		String query = sqlQueries.getQuery("select_parents_folders.sql");
		return jdbcNamesTpl.queryForList(query, parameters, Long.class);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public PrintNode loadRow(Long nodeid) throws SaodException {
		final String query = sqlQueries.getQuery("select_row_node_id.sql");
		final SqlRowSet queryForRowSet = this.jdbcTemplate.queryForRowSet(query, nodeid);

		while (queryForRowSet.next()) {
			PrintNode node = new PrintNode(nodeid); // ID
			node.setParent(queryForRowSet.getLong(2)); // PARENT
			node.setLocalContentSize(queryForRowSet.getLong(3));// LOCAL SIZE
			node.setSubdirContentSize(queryForRowSet.getLong(4));// SUM SIZE
			node.setNodetype(queryForRowSet.getInt(5));// TYPE
			node.setCountLocalFiles(queryForRowSet.getInt(6));// NUMBER LOCAL ELEMENTS
			node.setCountSubdirFiles(queryForRowSet.getInt(7));// NUMBER_SUM_ELEMENTS
			return node;
		}

		return null;
	}

}
