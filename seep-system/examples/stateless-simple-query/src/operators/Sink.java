/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
package operators;

import java.util.List;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

public class Sink implements StatelessOperator {

	private static final long serialVersionUID = 1L;
	
	public void setUp() {

	}

	// time control variables
	int c = 0;
	long init = 0;
	int sec = 0;

	public void processData(DataTuple dt) {
		int value1 = dt.getInt("value1");

		
		// TIME CONTROL
		c++;
		if((System.currentTimeMillis() - init) > 1000){
			System.out.println("SNK: "+sec+" "+c+" ");
			System.out.println("\033[34m" + value1 + " SINK SINK " + "\033[0m");
			c = 0;
			sec++;
			init = System.currentTimeMillis();
		}
	}
	
	public void processData(List<DataTuple> arg0) {
		System.out.println("\033[34m" + "SINK SINK SINK SINK SINK SINK SINK SINK SINK SINK SINK SINK " + "\033[0m");
		c++;
		if((System.currentTimeMillis() - init) > 1000){
			System.out.println("SNK: "+sec+" "+c+" ");
			c = 0;
			sec++;
			init = System.currentTimeMillis();
		}
	}
}
