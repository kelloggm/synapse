/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.mediators.db;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.dbcp.datasources.PerUserPoolDataSource;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.commons.datasource.*;
import org.apache.synapse.commons.datasource.factory.DataSourceFactory;
import org.apache.synapse.commons.jmx.MBeanRepository;
import org.apache.synapse.securevault.secret.SecretManager;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.mediators.AbstractMediator;

import javax.naming.Context;
import javax.sql.DataSource;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.util.*;
/**
 * This abstract DB mediator will perform common DB connection pooling etc. for all DB mediators
 */
public abstract class AbstractDBMediator extends AbstractMediator implements ManagedLifecycle {

    /** The information needed to create a data source */
    private DataSourceInformation dataSourceInformation;

    /** The name of the data source to lookup. */
    private String dataSourceName;

    /** The information needed to lookup a data source from a JNDI provider */
    private Properties jndiProperties = new Properties();
    
    /** The DataSource to get DB connections */
    private DataSource dataSource;

    /** MBean for DBPool monitoring */
    private DBPoolView dbPoolView;

    /** Statements */
    private final List<Statement> statementList = new ArrayList<Statement>();

    /** Map to store the pool configuration for de-serialization */
    private Map<Object, String> dataSourceProps = new HashMap<Object, String>();

    /**
     * Initializes the mediator - either an existing data source will be looked up
     * from an in- or external JNDI provider or a custom data source will be created
     * based on the provide configuration (using Apache DBCP).
     *  
     * @param se the Synapse environment reference
     */
    public void init(SynapseEnvironment se) {
        // check whether we shall try to lookup an existing data source or create a new custom data source
        if (dataSourceName != null) {
            dataSource = lookupDataSource(dataSourceName, jndiProperties);
        } else if (dataSourceInformation != null) {
            dataSource = createCustomDataSource(dataSourceInformation);
        }
    }

    /**
     * Destroys the mediator. If we are using our custom DataSource, then shut down the connections
     */
    public void destroy() {
        if (this.dataSource instanceof BasicDataSource) {
            try {
                ((BasicDataSource) this.dataSource).close();
                log.info("Successfully shut down DB connection pool for URL : " + getDSName());
            } catch (SQLException e) {
                log.warn("Error shutting down DB connection pool for URL : " + getDSName());
            }
        } else if (this.dataSource instanceof PerUserPoolDataSource) {
            ((PerUserPoolDataSource) this.dataSource).close();
            log.info("Successfully shut down DB connection pool for URL : " + getDSName());
        }
    }

    /**
     * Process each SQL statement against the current message
     * @param synCtx the current message
     * @return true, always
     */
    public boolean mediate(MessageContext synCtx) {

        String name = (this instanceof DBLookupMediator ? "DBLookup" : "DBReport");
        SynapseLog synLog = getLog(synCtx);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Start : " + name + " mediator");

            if (synLog.isTraceTraceEnabled()) {
                synLog.traceTrace("Message : " + synCtx.getEnvelope());
            }
        }

        for (Statement aStatement : statementList) {
            if (aStatement != null) {
                processStatement(aStatement, synCtx);
            }
        }

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("End : " + name + " mediator");
        }
        return true;
    }

    /**
     * Subclasses must specify how each SQL statement is processed
     * @param query the SQL statement
     * @param msgCtx current message
     */
    abstract protected void processStatement(Statement query, MessageContext msgCtx);

    /**
     * Return the name or (hopefully) unique connection URL specific to the DataSource being used
     * This is used for logging purposes only
     * @return a unique name or URL to refer to the DataSource being used
     */
    protected String getDSName() {
        String name = dataSourceInformation.getUrl();
        if (name == null) {
            name = dataSourceInformation.getDatasourceName();
        }
        return name;
    }
    
    public void setDataSourceInformation(DataSourceInformation dataSourceInformation) {
        this.dataSourceInformation = dataSourceInformation;
    }

    public void setJndiProperties(Properties jndiProperties) {
        this.jndiProperties = jndiProperties;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public void addDataSourceProperty(QName name, String value) {
        dataSourceProps.put(name, value);
    }

    public void addDataSourceProperty(String name, String value) {
        dataSourceProps.put(name, value);
    }

    public void addStatement(Statement stmnt) {
        statementList.add(stmnt);
    }

    public List<Statement> getStatementList() {
        return statementList;
    }

    public DBPoolView getDbPoolView() {
        return dbPoolView;
    }

    public void setDbPoolView(DBPoolView dbPoolView) {
        this.dbPoolView = dbPoolView;
    }

    /**
     * Return a Prepared statement for the given Statement object, which is ready to be executed
     * @param stmnt
     * @param msgCtx
     * @return
     * @throws SQLException
     */
    protected PreparedStatement getPreparedStatement(Statement stmnt,
                                                     MessageContext msgCtx) throws SQLException {

        SynapseLog synLog = getLog(msgCtx);

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Getting a connection from DataSource " + getDSName() +
                " and preparing statement : " + stmnt.getRawStatement());
        }

        Connection con = getDataSource().getConnection();
        if (con == null) {
            String msg = "Connection from DataSource " + getDSName() + " is null.";
            log.error(msg);
            throw new SynapseException(msg);
        }

        if (dataSource instanceof BasicDataSource) {

            BasicDataSource basicDataSource = (BasicDataSource) dataSource;
            int numActive = basicDataSource.getNumActive();
            int numIdle = basicDataSource.getNumIdle();
            String connectionId = Integer.toHexString(con.hashCode());

            DBPoolView dbPoolView = getDbPoolView();
            if (dbPoolView != null) {
                dbPoolView.setNumActive(numActive);
                dbPoolView.setNumIdle(numIdle);
                dbPoolView.updateConnectionUsage(connectionId);
            }

            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("[ DB Connection : " + con + " ]");
                synLog.traceOrDebug("[ DB Connection instance identifier : " +
                        connectionId + " ]");
                synLog.traceOrDebug("[ Number of Active Connection : " + numActive + " ]");
                synLog.traceOrDebug("[ Number of Idle Connection : " + numIdle + " ]");
            }
        }

        PreparedStatement ps = con.prepareStatement(stmnt.getRawStatement());

        // set parameters if any
        List<Statement.Parameter> params = stmnt.getParameters();
        int column = 1;

        for (Statement.Parameter param : params) {
            if (param == null) {
                continue;
            }
            String value = (param.getPropertyName() != null ?
                    param.getPropertyName() : param.getXpath().stringValueOf(msgCtx));

            if (synLog.isTraceOrDebugEnabled()) {
                synLog.traceOrDebug("Setting as parameter : " + column + " value : " + value +
                        " as JDBC Type : " + param.getType() + "(see java.sql.Types for valid " +
                        "types)");
            }

            switch (param.getType()) {
                // according to J2SE 1.5 /docs/guide/jdbc/getstart/mapping.html
                case Types.CHAR:
                case Types.VARCHAR:
                case Types.LONGVARCHAR: {
                    ps.setString(column++, value);
                    break;
                }
                case Types.NUMERIC:
                case Types.DECIMAL: {
                    ps.setBigDecimal(column++, new BigDecimal(value));
                    break;
                }
                case Types.BIT: {
                    ps.setBoolean(column++, Boolean.parseBoolean(value));
                    break;
                }
                case Types.TINYINT: {
                    ps.setByte(column++, Byte.parseByte(value));
                    break;
                }
                case Types.SMALLINT: {
                    ps.setShort(column++, Short.parseShort(value));
                    break;
                }
                case Types.INTEGER: {
                    ps.setInt(column++, Integer.parseInt(value));
                    break;
                }
                case Types.BIGINT: {
                    ps.setLong(column++, Long.parseLong(value));
                    break;
                }
                case Types.REAL: {
                    ps.setFloat(column++, Float.parseFloat(value));
                    break;
                }
                case Types.FLOAT: {
                    ps.setDouble(column++, Double.parseDouble(value));
                    break;
                }
                case Types.DOUBLE: {
                    ps.setDouble(column++, Double.parseDouble(value));
                    break;
                }
                // skip BINARY, VARBINARY and LONGVARBINARY
                case Types.DATE: {
                    ps.setDate(column++, Date.valueOf(value));
                    break;
                }
                case Types.TIME: {
                    ps.setTime(column++, Time.valueOf(value));
                    break;
                }
                case Types.TIMESTAMP: {
                    ps.setTimestamp(column++, Timestamp.valueOf(value));
                    break;
                }
                // skip CLOB, BLOB, ARRAY, DISTINCT, STRUCT, REF, JAVA_OBJECT
                default: {
                    String msg = "Trying to set an un-supported JDBC Type : " + param.getType() +
                            " against column : " + column + " and statement : " +
                            stmnt.getRawStatement() +
                            " used by a DB mediator against DataSource : " + getDSName() +
                            " (see java.sql.Types for valid type values)";
                    handleException(msg, msgCtx);
                }
            }
        }

        if (synLog.isTraceOrDebugEnabled()) {
            synLog.traceOrDebug("Successfully prepared statement : " + stmnt.getRawStatement() +
                " against DataSource : " + getDSName());
        }
        return ps;
    }

    /**
     * Lookup the DataSource on JNDI using the specified name and optional properties
     *
     * @param dataSourceName the name of the data source to lookup
     * @param jndiProperties the JNDI properties identifying a data source provider  
     * 
     * @return a DataSource looked up using the specified JNDI properties
     */
    private DataSource lookupDataSource(String dataSourceName, Properties jndiProperties) {

        DataSource dataSource = null;
        RepositoryBasedDataSourceFinder finder = DataSourceRepositoryHolder.getInstance()
                .getRepositoryBasedDataSourceFinder();

        if (finder.isInitialized()) {
            // first try a lookup based on the data source name only
            dataSource = finder.find(dataSourceName);
        }

        if(dataSource == null){
        // decrypt the password if needed
            String password = jndiProperties.getProperty(Context.SECURITY_CREDENTIALS);
            if (password != null && !"".equals(password)) {
                jndiProperties.put(Context.SECURITY_CREDENTIALS, getActualPassword(password));
            }

            // lookup the data source using the specified jndi properties
            dataSource = DataSourceFinder.find(dataSourceName, jndiProperties);
            if (dataSource == null) {
                handleException("Cannot find a DataSource " + dataSourceName + " for given JNDI" +
                        " properties :" + jndiProperties);
            }
        }
        MBeanRepository mBeanRepository = DatasourceMBeanRepository.getInstance();
        Object mBean = mBeanRepository.getMBean(dataSourceName);
        if (mBean instanceof DBPoolView) {
            setDbPoolView((DBPoolView) mBean);
        }
        log.info("Sunccessfully looked up datasource " + dataSourceName + ".");

        return dataSource;
    }

    /**
     * Create a custom DataSource using the specified data source information.
     *
     * @param dataSourceInformation the data source information to create a data source
     * 
     * @return a DataSource created using specified properties
     */
    protected DataSource createCustomDataSource(DataSourceInformation dataSourceInformation) {
        
        DataSource dataSource = DataSourceFactory.createDataSource(dataSourceInformation);
        if (dataSource != null) {
            log.info("Sunccessfully created data source for " + dataSourceInformation.getUrl() + ".");
        }
        
        return dataSource;
    }

    /**
     * Get the password from SecretManager . here only use SecretManager
     *
     * @param aliasPasword alias password
     * @return if the SecretManager is initiated , then , get the corresponding secret
     *         , else return alias itself
     */
    private String getActualPassword(String aliasPasword) {
        SecretManager secretManager = SecretManager.getInstance();
        if (secretManager.isInitialized()) {
            return secretManager.getSecret(aliasPasword);
        }
        return aliasPasword;
    }

    protected void handleException(String message) {
        LogFactory.getLog(this.getClass()).error(message);
        throw new SynapseException(message);
    }

    public Map<Object, String> getDataSourceProps() {
        return dataSourceProps;
    }
}