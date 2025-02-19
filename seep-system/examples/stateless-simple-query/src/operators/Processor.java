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

public class Processor implements StatelessOperator{

	private static final long serialVersionUID = 1L;

	
	public void processData(DataTuple data) {
		int value1 = data.getInt("value1");
		int value2 = data.getInt("value2");
		int value3 = data.getInt("value3");
		
		value1 = value2+value3;
		value2 = value2/value3;
		value3 = value3*value3;

		System.out.println("\033[32m" + "PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR " + "\033[0m");

		DataTuple outputTuple = data.setValues(value1, value2, value3);
		api.send(outputTuple);
	}

	
	public void processData(List<DataTuple> arg0) {
		System.out.println("\033[32m" + "PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR PROCESSOR " + "\033[0m");

		DataTuple data = arg0.get(0);
		DataTuple outputTuple = data.setValues(1, 2, 3);
		// api.send(outputTuple);
		api.send_highestWeight(outputTuple);

	}

	
	public void setUp() {
		// TODO Auto-generated method stub
		
	}

}
