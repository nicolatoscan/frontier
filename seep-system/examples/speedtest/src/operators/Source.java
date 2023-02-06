package operators;

import java.util.List;
import java.util.Map;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;


public class Source implements StatelessOperator {

	private static final long serialVersionUID = 1L;
	private int tuplesSent = 0;
	
	public void setUp() { }
	
	public void processData(DataTuple dt) {
		Map<String, Integer> mapper = api.getDataMapper();
		DataTuple data = new DataTuple(mapper, new TuplePayload());
		long start = System.currentTimeMillis();

		
		while(tuplesSent <= 100 * 1000){
			DataTuple output = data.newTuple(tuplesSent);
			api.send(output);
			tuplesSent++;

			if (tuplesSent % 1000 == 0) {
				System.out.println("PY,SOURCE," + tuplesSent);
			}

			// long toSleep = start + tuplesSent - System.currentTimeMillis();
			// if (toSleep > 0) {
			// 	try {
			// 		Thread.sleep(toSleep);
			// 	} catch (InterruptedException e) {
			// 		e.printStackTrace();
			// 	}
			// }
		}
	}
	
	public void processData(List<DataTuple> arg0) {
		// TODO Auto-generated method stub
		
	}
}
