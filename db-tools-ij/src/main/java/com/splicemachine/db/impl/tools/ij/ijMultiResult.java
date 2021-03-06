/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.tools.ij;

import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.SQLWarning;

/**
 * This is an impl for a statement execution; the result
 * is either an update count or result set depending
 * on what was executed.
 *
 */
class ijMultiResult extends ijResultImpl {

	private Statement statement;
	private ResultSet rs;
	boolean closeWhenDone;

	ijMultiResult(Statement s, ResultSet rs, boolean c) {
		statement = s;
		this.rs = rs;
		closeWhenDone = c;
	}

	public boolean isMulti() { return true; }

	public Statement getStatement() { return statement; }
	public ResultSet getResultSet() { return rs; }
	public void closeStatement() throws SQLException { if (closeWhenDone) statement.close(); }

	public SQLWarning getSQLWarnings() { return null; }
	public void clearSQLWarnings() { }
}
