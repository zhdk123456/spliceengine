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

package com.splicemachine.db.catalog.types;

import com.splicemachine.db.iapi.services.io.StoredFormatIds;
import com.splicemachine.db.iapi.services.io.FormatableInstanceGetter;

public class TypesImplInstanceGetter extends FormatableInstanceGetter {

        public Object getNewInstance() {

                switch (fmtId) {
                  case StoredFormatIds.BOOLEAN_TYPE_ID_IMPL:
                  case StoredFormatIds.INT_TYPE_ID_IMPL:
                  case StoredFormatIds.SMALLINT_TYPE_ID_IMPL:
                  case StoredFormatIds.TINYINT_TYPE_ID_IMPL:
                  case StoredFormatIds.LONGINT_TYPE_ID_IMPL:
                  case StoredFormatIds.DOUBLE_TYPE_ID_IMPL:
                  case StoredFormatIds.REAL_TYPE_ID_IMPL:
                  case StoredFormatIds.REF_TYPE_ID_IMPL:
                  case StoredFormatIds.CHAR_TYPE_ID_IMPL:
                  case StoredFormatIds.VARCHAR_TYPE_ID_IMPL:
                  case StoredFormatIds.LONGVARCHAR_TYPE_ID_IMPL:
                  case StoredFormatIds.BIT_TYPE_ID_IMPL:
                  case StoredFormatIds.VARBIT_TYPE_ID_IMPL:
                  case StoredFormatIds.LONGVARBIT_TYPE_ID_IMPL:
                  case StoredFormatIds.DATE_TYPE_ID_IMPL:
                  case StoredFormatIds.TIME_TYPE_ID_IMPL:
                  case StoredFormatIds.TIMESTAMP_TYPE_ID_IMPL:
                  case StoredFormatIds.BLOB_TYPE_ID_IMPL:
                  case StoredFormatIds.CLOB_TYPE_ID_IMPL:
                  case StoredFormatIds.XML_TYPE_ID_IMPL:
                          return new BaseTypeIdImpl(fmtId);
                  case StoredFormatIds.DECIMAL_TYPE_ID_IMPL:
                          return new DecimalTypeIdImpl(false);
                  case StoredFormatIds.DATA_TYPE_SERVICES_IMPL_V01_ID:
                      return new OldRoutineType();
                  default:
                        return null;
                }
        }
}