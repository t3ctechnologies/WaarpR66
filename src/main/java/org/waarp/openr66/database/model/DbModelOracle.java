/**
 * This file is part of Waarp Project.
 * 
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author tags. See the
 * COPYRIGHT.txt in the distribution for a full listing of individual contributors.
 * 
 * All Waarp Project is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Waarp is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Waarp . If not, see
 * <http://www.gnu.org/licenses/>.
 */
package org.waarp.openr66.database.model;

import java.sql.SQLException;

import org.waarp.common.database.DbPreparedStatement;
import org.waarp.common.database.DbRequest;
import org.waarp.common.database.DbSession;
import org.waarp.common.database.exception.WaarpDatabaseException;
import org.waarp.common.database.exception.WaarpDatabaseNoConnectionException;
import org.waarp.common.database.exception.WaarpDatabaseNoDataException;
import org.waarp.common.database.exception.WaarpDatabaseSqlException;
import org.waarp.openr66.database.DbConstant;
import org.waarp.openr66.database.data.DbConfiguration;
import org.waarp.openr66.database.data.DbHostAuth;
import org.waarp.openr66.database.data.DbHostConfiguration;
import org.waarp.openr66.database.data.DbMultipleMonitor;
import org.waarp.openr66.database.data.DbRule;
import org.waarp.openr66.database.data.DbTaskRunner;
import org.waarp.openr66.protocol.configuration.Configuration;
import org.waarp.openr66.protocol.configuration.PartnerConfiguration;

/**
 * Oracle Database Model implementation
 * 
 * @author Frederic Bregier
 * 
 */
public class DbModelOracle extends org.waarp.common.database.model.DbModelOracle {
	/**
	 * Create the object and initialize if necessary the driver
	 * 
	 * @param dbserver
	 * @param dbuser
	 * @param dbpasswd
	 * @throws WaarpDatabaseNoConnectionException
	 */
	public DbModelOracle(String dbserver, String dbuser, String dbpasswd)
			throws WaarpDatabaseNoConnectionException {
		super(dbserver, dbuser, dbpasswd);
	}

	@Override
	public void createTables(DbSession session) throws WaarpDatabaseNoConnectionException {
		// Create tables: configuration, hosts, rules, runner, cptrunner
		String createTableH2 = "CREATE TABLE ";
		String constraint = " CONSTRAINT ";
		String primaryKey = " PRIMARY KEY ";
		String notNull = " NOT NULL ";

		// Multiple Mode
		String action = createTableH2 + DbMultipleMonitor.table + "(";
		DbMultipleMonitor.Columns[] mcolumns = DbMultipleMonitor.Columns
				.values();
		for (int i = 0; i < mcolumns.length - 1; i++) {
			action += mcolumns[i].name() +
					DBType.getType(DbMultipleMonitor.dbTypes[i]) + notNull +
					", ";
		}
		action += mcolumns[mcolumns.length - 1].name() +
				DBType.getType(DbMultipleMonitor.dbTypes[mcolumns.length - 1]) +
				notNull + ",";
		action += constraint + " multimonit_pk " + primaryKey + "("
				+ mcolumns[mcolumns.length - 1].name() + "))";
		System.out.println(action);
		DbRequest request = new DbRequest(session);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			e.printStackTrace();
			// XXX FIX no return;
		} finally {
			request.close();
		}
		DbMultipleMonitor multipleMonitor = new DbMultipleMonitor(session,
				Configuration.configuration.HOST_ID, 0, 0, 0);
		try {
			if (!multipleMonitor.exist()) {
				multipleMonitor.insert();
			}
		} catch (WaarpDatabaseException e1) {
			e1.printStackTrace();
		}

		// Configuration
		action = createTableH2 + DbConfiguration.table + "(";
		DbConfiguration.Columns[] ccolumns = DbConfiguration.Columns
				.values();
		for (int i = 0; i < ccolumns.length - 1; i++) {
			action += ccolumns[i].name() +
					DBType.getType(DbConfiguration.dbTypes[i]) + notNull +
					", ";
		}
		action += ccolumns[ccolumns.length - 1].name() +
				DBType.getType(DbConfiguration.dbTypes[ccolumns.length - 1]) +
				notNull + ",";
		action += constraint + " conf_pk " + primaryKey + "("
				+ ccolumns[ccolumns.length - 1].name() + "))";
		System.out.println(action);
		request = new DbRequest(session);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			// XXX FIX no return;
		} finally {
			request.close();
		}

		// HostConfiguration
		action = createTableH2 + DbHostConfiguration.table + "(";
		DbHostConfiguration.Columns[] chcolumns = DbHostConfiguration.Columns
				.values();
		for (int i = 0; i < chcolumns.length - 1; i++) {
			action += chcolumns[i].name() +
					DBType.getType(DbHostConfiguration.dbTypes[i]) + notNull +
					", ";
		}
		action += chcolumns[chcolumns.length - 1].name() +
				DBType.getType(DbHostConfiguration.dbTypes[chcolumns.length - 1]) +
				notNull + ",";
		action += constraint + " hostconf_pk " + primaryKey + "("
				+ chcolumns[chcolumns.length - 1].name() + "))";
		System.out.println(action);
		request = new DbRequest(session);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			// XXX FIX no return;
		} finally {
			request.close();
		}

		// hosts
		action = createTableH2 + DbHostAuth.table + "(";
		DbHostAuth.Columns[] hcolumns = DbHostAuth.Columns.values();
		for (int i = 0; i < hcolumns.length - 1; i++) {
			action += hcolumns[i].name() +
					DBType.getType(DbHostAuth.dbTypes[i]) + notNull + ", ";
		}
		action += hcolumns[hcolumns.length - 1].name() +
				DBType.getType(DbHostAuth.dbTypes[hcolumns.length - 1]) +
				notNull + ",";
		action += constraint + " host_pk " + primaryKey + "("
				+ hcolumns[hcolumns.length - 1].name() + "))";
		System.out.println(action);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			// XXX FIX no return;
		} finally {
			request.close();
		}

		// rules
		action = createTableH2 + DbRule.table + "(";
		DbRule.Columns[] rcolumns = DbRule.Columns.values();
		for (int i = 0; i < rcolumns.length - 1; i++) {
			action += rcolumns[i].name() +
					DBType.getType(DbRule.dbTypes[i]) + ", ";
		}
		action += rcolumns[rcolumns.length - 1].name() +
				DBType.getType(DbRule.dbTypes[rcolumns.length - 1]) +
				notNull + ",";
		action += constraint + " rule_pk " + primaryKey + "("
				+ rcolumns[rcolumns.length - 1].name() + "))";
		System.out.println(action);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			// XXX FIX no return;
		} finally {
			request.close();
		}

		// runner
		action = createTableH2 + DbTaskRunner.table + "(";
		DbTaskRunner.Columns[] acolumns = DbTaskRunner.Columns.values();
		for (int i = 0; i < acolumns.length; i++) {
			action += acolumns[i].name() +
					DBType.getType(DbTaskRunner.dbTypes[i]) + notNull + ", ";
		}
		// Several columns for primary key
		action += constraint + " runner_pk " + primaryKey + "(";
		for (int i = DbTaskRunner.NBPRKEY; i > 1; i--) {
			action += acolumns[acolumns.length - i].name() + ",";
		}
		action += acolumns[acolumns.length - 1].name() + "))";
		System.out.println(action);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			// XXX FIX no return;
		} finally {
			request.close();
		}
		// Index Runner
		action = "CREATE INDEX IDX_RUNNER ON " + DbTaskRunner.table + "(";
		DbTaskRunner.Columns[] icolumns = DbTaskRunner.indexes;
		for (int i = 0; i < icolumns.length - 1; i++) {
			action += icolumns[i].name() + ", ";
		}
		action += icolumns[icolumns.length - 1].name() + ")";
		System.out.println(action);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			// XXX FIX no return;
		} finally {
			request.close();
		}

		// cptrunner
		action = "CREATE SEQUENCE " + DbTaskRunner.fieldseq +
				" MINVALUE " + (DbConstant.ILLEGALVALUE + 1) +
				" START WITH " + (DbConstant.ILLEGALVALUE + 1);
		System.out.println(action);
		try {
			request.query(action);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			return;
		} finally {
			request.close();
		}
	}

	public boolean upgradeDb(DbSession session, String version) throws WaarpDatabaseNoConnectionException {
		if (PartnerConfiguration.isVersion2GEQVersion1(version, "2.4.13")) {
			System.out.println(version+" to 2.4.13? "+PartnerConfiguration.isVersion2GEQVersion1(version, "2.4.13"));
			String createTableH2 = "CREATE TABLE ";
			String constraint = " CONSTRAINT ";
			String primaryKey = " PRIMARY KEY ";
			String notNull = " NOT NULL ";
	
			// HostConfiguration
			String action = createTableH2 + DbHostConfiguration.table + "(";
			DbHostConfiguration.Columns[] chcolumns = DbHostConfiguration.Columns
					.values();
			for (int i = 0; i < chcolumns.length - 1; i++) {
				action += chcolumns[i].name() +
						DBType.getType(DbHostConfiguration.dbTypes[i]) + notNull +
						", ";
			}
			action += chcolumns[chcolumns.length - 1].name() +
					DBType.getType(DbHostConfiguration.dbTypes[chcolumns.length - 1]) +
					notNull + ",";
			action += constraint + " hostconf_pk " + primaryKey + "("
					+ chcolumns[chcolumns.length - 1].name() + "))";
			System.out.println(action);
			DbRequest request = new DbRequest(session);
			try {
				request.query(action);
			} catch (WaarpDatabaseSqlException e) {
				e.printStackTrace();
				return false;
			} finally {
				request.close();
			}
		}
		if (PartnerConfiguration.isVersion2GEQVersion1(version, "2.5.0")) {
			String command = "ALTER TABLE "+DbTaskRunner.table+" ADD ( "+
					DbTaskRunner.Columns.TRANSFERINFO.name()+ " "+
					DBType.getType(DbTaskRunner.dbTypes[DbTaskRunner.Columns.TRANSFERINFO.ordinal()]) + 
					" DEFAULT '{}' NOT NULL ) ";
			DbRequest request = new DbRequest(session);
			try {
				request.query(command);
			} catch (WaarpDatabaseSqlException e) {
				e.printStackTrace();
				return false;
			} finally {
				request.close();
			}
		}
		return true;
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.waarp.openr66.databaseold.model.DbModel#resetSequence()
	 */
	@Override
	public void resetSequence(DbSession session, long newvalue)
			throws WaarpDatabaseNoConnectionException {
		String action = "DROP SEQUENCE " + DbTaskRunner.fieldseq;
		String action2 = "CREATE SEQUENCE " + DbTaskRunner.fieldseq +
				" MINVALUE " + (DbConstant.ILLEGALVALUE + 1) +
				" START WITH " + (newvalue);
		DbRequest request = new DbRequest(session);
		try {
			request.query(action);
			request.query(action2);
		} catch (WaarpDatabaseNoConnectionException e) {
			e.printStackTrace();
			return;
		} catch (WaarpDatabaseSqlException e) {
			e.printStackTrace();
			return;
		} finally {
			request.close();
		}

		System.out.println(action);
	}

	/*
	 * (non-Javadoc)
	 * @see org.waarp.openr66.databaseold.model.DbModel#nextSequence()
	 */
	@Override
	public long nextSequence(DbSession dbSession)
			throws WaarpDatabaseNoConnectionException,
			WaarpDatabaseSqlException, WaarpDatabaseNoDataException {
		long result = DbConstant.ILLEGALVALUE;
		String action = "SELECT " + DbTaskRunner.fieldseq + ".NEXTVAL FROM DUAL";
		DbPreparedStatement preparedStatement = new DbPreparedStatement(
				dbSession);
		try {
			preparedStatement.createPrepareStatement(action);
			// Limit the search
			preparedStatement.executeQuery();
			if (preparedStatement.getNext()) {
				try {
					result = preparedStatement.getResultSet().getLong(1);
				} catch (SQLException e) {
					throw new WaarpDatabaseSqlException(e);
				}
				return result;
			} else {
				throw new WaarpDatabaseNoDataException(
						"No sequence found. Must be initialized first");
			}
		} finally {
			preparedStatement.realClose();
		}
	}
	
	public boolean needUpgradeDb(DbSession session, String version, boolean tryFix)
			throws WaarpDatabaseNoConnectionException {
		// Check if the database is up to date
		DbRequest request = null;
		if (PartnerConfiguration.isVersion2GEQVersion1(version, "2.4.13")) {
			try {
				request = new DbRequest(DbConstant.admin.session);
				request.select("select "+DbHostConfiguration.Columns.HOSTID.name()+" from "+DbHostConfiguration.table+
						" where "+DbHostConfiguration.Columns.HOSTID+" = '"+Configuration.configuration.HOST_ID+"'");
				request.close();
			} catch (WaarpDatabaseSqlException e) {
				return ! upgradeDb(session, version);
			} finally {
				if (request != null) {
					request.close();
				}
			}
		}
		request = null;
		if (PartnerConfiguration.isVersion2GEQVersion1(version, "2.5.0")) {
			try {
				request = new DbRequest(DbConstant.admin.session);
				request.select("select "+DbTaskRunner.Columns.TRANSFERINFO.name()+" from "+DbTaskRunner.table+
						" where "+DbTaskRunner.Columns.SPECIALID+" = "+DbConstant.ILLEGALVALUE);
				request.close();
			} catch (WaarpDatabaseSqlException e) {
				return ! upgradeDb(session, version);
			} finally {
				if (request != null) {
					request.close();
				}
			}
		}
		return false;
	}

}
