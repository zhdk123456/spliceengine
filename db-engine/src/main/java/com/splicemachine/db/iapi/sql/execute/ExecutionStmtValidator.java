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

package com.splicemachine.db.iapi.sql.execute;

import com.splicemachine.db.iapi.error.StandardException;

/**
 * An ExecutionStatementValidator is an object that is handed a ConstantAction and asked whether it is ok for
 * this result set to execute.  When something like a trigger is executing, one of these gets pushed.
 * Before execution, each validator that has been pushed is invoked on the result set that we are about to
 * execution.  It is up to the validator to look at the result set and either complain (throw an exception)
 * or let it through.
 */
public interface ExecutionStmtValidator {
    /**
     * Validate the statement.
     *
     * @param constantAction The constant action that we are about to execute.
     */
    void validateStatement(ConstantAction constantAction) throws StandardException;
}
