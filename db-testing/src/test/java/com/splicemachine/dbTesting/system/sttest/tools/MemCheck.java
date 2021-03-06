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
package com.splicemachine.dbTesting.system.sttest.tools;

import java.util.Date;

public class MemCheck extends Thread {
	int delay = 200000;
	
	public boolean stopNow = false;
	
	public MemCheck() {
	}
	
	public MemCheck(int num) {
		delay = num;
	}
	
	public void run() {
		while (stopNow == false) {
			try {
				showmem();
				sleep(delay);
			} catch (java.lang.InterruptedException ie) {
				System.out.println("memcheck: unexpected error in sleep");
			}
		}
	}
	
	public static void showmem() {
		Runtime rt = null;
		Date d = null;
		rt = Runtime.getRuntime();
		d = new Date();
		System.out.println("total memory: " + rt.totalMemory() + " free: "
				+ rt.freeMemory() + " " + d.toString());
	}
	
	public static void main(String argv[]) {
		System.out.println("MemCheck starting");
		MemCheck mc = new MemCheck();
		mc.run();
	}
}