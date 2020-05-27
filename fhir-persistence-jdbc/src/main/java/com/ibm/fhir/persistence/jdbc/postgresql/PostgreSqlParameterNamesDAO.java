/*
 * (C) Copyright IBM Corp. 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.persistence.jdbc.postgresql;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.fhir.persistence.jdbc.dao.impl.ParameterNameDAOImpl;
import com.ibm.fhir.persistence.jdbc.exception.FHIRPersistenceDataAccessException;

public class PostgreSqlParameterNamesDAO extends ParameterNameDAOImpl {
    private static final Logger log = Logger.getLogger(PostgreSqlParameterNamesDAO.class.getName());
    private static final String CLASSNAME = PostgreSqlParameterNamesDAO.class.getName();
    private static final String SQL_CALL_ADD_PARAMETER_NAME = "{CALL %s.add_parameter_name(?, ?)}";

    public PostgreSqlParameterNamesDAO(Connection c) {
        super(c);
    }

    /**
     * Calls a stored procedure to read the name contained in the passed Parameter in the Parameter_Names table.
     * If it's not in the DB, it will be stored and a unique id will be returned.
     * @param parameterName
     * @return The generated id of the stored system.
     * @throws FHIRPersistenceDataAccessException
     */
    @Override
    public int readOrAddParameterNameId(String parameterName) throws FHIRPersistenceDataAccessException  {
        final String METHODNAME = "readOrAddParameterNameId";
        log.entering(CLASSNAME, METHODNAME);

        int parameterNameId;
        String currentSchema;
        String stmtString;
        String errMsg = "Failure storing search parameter name id: name=" + parameterName;
        long dbCallStartTime;
        double dbCallDuration;

        try {
            // TODO: schema should be known by application. Fix to avoid an extra round-trip.
            currentSchema = getConnection().getSchema().trim();
            stmtString = String.format(SQL_CALL_ADD_PARAMETER_NAME, currentSchema);
            try (CallableStatement stmt = getConnection().prepareCall(stmtString)) {
                stmt.setString(1, parameterName);
                stmt.registerOutParameter(2, Types.INTEGER);
                dbCallStartTime = System.nanoTime();
                stmt.execute();
                dbCallDuration = (System.nanoTime()-dbCallStartTime)/1e6;
                if (log.isLoggable(Level.FINE)) {
                        log.fine("DB read/store parameter name id complete. executionTime=" + dbCallDuration + "ms");
                }
                parameterNameId = stmt.getInt(2);
            }
        } catch (Throwable e) {
            throw new FHIRPersistenceDataAccessException(errMsg,e);
        } finally {
            log.exiting(CLASSNAME, METHODNAME);
        }
        return parameterNameId;
    }
}
