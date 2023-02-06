package operators;

import java.util.Date;
import java.util.List;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

public class Sink implements StatelessOperator {

	private static final long serialVersionUID = 1L;
	private int tupleReceived = 0;
	private long lastDate = new Date().getTime();
	
	public void setUp() { }

	public void processData(DataTuple dt) {
		int value1 = dt.getInt("value1");
		tupleReceived++;

		if(tupleReceived % 1000 == 0){
			long now = new Date().getTime();
			System.out.println("PY,SINK,TupleReceived=" + tupleReceived + ",value=" + value1 + ",time=" + (now - this.lastDate));
			this.lastDate = now;
		}
	}
	
	public void processData(List<DataTuple> arg0) {

	}
}
