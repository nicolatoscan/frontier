package operators;

import java.util.List;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

public class Sink implements StatelessOperator {

	private static final long serialVersionUID = 1L;
	private int tupleReceived = 0;
	
	public void setUp() { }

	public void processData(DataTuple dt) {
		int value1 = dt.getInt("value1");
		tupleReceived++;
		if(tupleReceived % 1000 == 0){
			System.out.println("PY,SINK," + tupleReceived + "," + value1);
		}
	}
	
	public void processData(List<DataTuple> arg0) {

	}
}
