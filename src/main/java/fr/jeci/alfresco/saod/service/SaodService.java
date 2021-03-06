package fr.jeci.alfresco.saod.service;

import java.util.List;

import fr.jeci.alfresco.saod.SaodException;
import fr.jeci.alfresco.saod.pojo.PrintNode;

public interface SaodService {

	/**
	 * Load data from Alfresco DB into local DB
	 * 
	 * @throws SaodException
	 */
	void loadDataFromAlfrescoDB() throws SaodException;

	/**
	 * List root folders
	 * 
	 * @return list of id
	 */
	List<PrintNode> getRoots() throws SaodException;


	/**
	 * Load printable node
	 * 
	 * @param nodeid
	 * @return
	 * @throws SaodException
	 */
	PrintNode loadPrintNode(String nodeid) throws SaodException;

	/**
	 * Compute path of this node
	 * 
	 * @param nodeid
	 * @return
	 * @throws SaodException
	 */
	String computePath(String nodeid) throws SaodException;

	/**
	 * Compute path of this node
	 * 
	 * @param parent
	 * @param nodeid
	 * @param separator
	 * @return
	 * @throws SaodException
	 */
	String computePath(String parent, String nodeid, String separator) throws SaodException;

	/**
	 * Compute full size of all directory starting from leaf to root
	 * 
	 * @throws SaodException
	 */
	void resetFullSumSize() throws SaodException;

	/**
	 * Return a string with date of last run and duration.
	 * 
	 * @return compute string or "Empty database"
	 */
	String lastRunMessage();

	/**
	 * Return is compute is currently running
	 * 
	 * @return true if compute is running.
	 */
	boolean isRunning();

	/**
	 * Get printable node to all children
	 * 
	 * @param nodeid
	 * @param recursive (default true)
	 * @return
	 * @throws SaodException
	 */
	List<PrintNode> getAllChildren(Long nodeid) throws SaodException;
	
	List<PrintNode> getAllChildren(Long nodeid, boolean recursive) throws SaodException;

	/**
	 * Permit to export files, directories or both
	 * 
	 * @param nodeid
	 * @param type
	 * @return
	 * @throws SaodException
	 */
	List<PrintNode> getExport(final String nodeid, String type) throws SaodException;



}
