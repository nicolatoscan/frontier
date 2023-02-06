package operators;

import java.util.List;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

public class Processor implements StatelessOperator{

	private static final long serialVersionUID = 1L;
	private int tupleProcessed = 0;

	
	public void processData(DataTuple data) {
		int value1 = data.getInt("value1");

		DataTuple outputTuple = data.setValues(value1);
		api.send(outputTuple);

		tupleProcessed++;
		if(tupleProcessed % 1000 == 0){
			System.out.println("PY,PROCESSOR," + tupleProcessed + "," + value1);
		}
	}

	
	public void processData(List<DataTuple> arg0) {
	}

	
	public void setUp() {
		// TODO Auto-generated method stub
		
	}

}
