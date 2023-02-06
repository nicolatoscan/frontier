import java.util.ArrayList;

import operators.Processor;
import operators.Sink;
import operators.Source;
import uk.ac.imperial.lsds.seep.api.QueryBuilder;
import uk.ac.imperial.lsds.seep.api.QueryComposer;
import uk.ac.imperial.lsds.seep.api.QueryPlan;
import uk.ac.imperial.lsds.seep.operator.Connectable;

public class Base implements QueryComposer{

	public QueryPlan compose() {
		Connectable src = QueryBuilder.newStatelessSource(new Source(), -1, this.getFields());
		Connectable p = QueryBuilder.newStatelessOperator(new Processor(), 1, this.getFields());
		Connectable snk = QueryBuilder.newStatelessSink(new Sink(), -2, this.getFields());
		src.connectTo(p, true, 0);
		p.connectTo(snk, true, 0);
		return QueryBuilder.build();
	}

	private ArrayList<String> getFields() {
		ArrayList<String> srcFields = new ArrayList<String>();
		srcFields.add("value1");
		return srcFields;
	}
}
