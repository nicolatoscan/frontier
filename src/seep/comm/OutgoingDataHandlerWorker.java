package seep.comm;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

import seep.comm.serialization.DataTuple;
import seep.comm.serialization.messages.TuplePayload;
import seep.infrastructure.NodeManager;
import seep.runtimeengine.AsynchronousCommunicationChannel;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class OutgoingDataHandlerWorker implements Runnable{

	// TX data
	private Selector selector;
	
	private boolean goOn = true;
	
	public OutgoingDataHandlerWorker(Selector selector){
		this.selector = selector;
	}
	
	@Override
	public void run() {
		while(goOn){
			try {
				// Check events
				selector.select();	
				//Iterate on the events if any
				Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
				while(selectedKeys.hasNext()){
					// We choose one key
					SelectionKey key = (SelectionKey) selectedKeys.next();
					selectedKeys.remove();	
					// Sanity check
					if(!key.isValid()){
						continue;
					}
					// Check the write event
					if(key.isWritable()){
						write(key);
					}
				}
			}
			catch (IOException e) {
				NodeManager.nLogger.severe("-> While checking the selector events: "+e.getMessage());
				e.printStackTrace();
			}
		}
		System.out.println("######################################");
		System.exit(-1);
	}
	
	private void write(SelectionKey key){
		// Retrieve socket
		SocketChannel sc = (SocketChannel) key.channel();
		// And retrieve native ByteBuffer
		AsynchronousCommunicationChannel acc = (AsynchronousCommunicationChannel) key.attachment();
		
		// Socket is ready to write, check if batch is complete or not...
		if(acc.isBatchAvailable()){
			Output o = acc.getOutput();
			// Pick the buffer to send
			ByteBuffer nb = ((ByteBufferOutputStream)o.getOutputStream()).getByteBuffer();
			try {
				synchronized(nb){
					nb.flip();
					int bytesWritten = sc.write(nb);
					nb.clear();
//					o.clear();
	                synchronized(acc){
	                	acc.resetBatch();
	                	acc.notify();
	                }
				}
			}
			catch (IOException e) {
				NodeManager.nLogger.severe("-> While trying to write in the aync sc: "+e.getMessage());
				e.printStackTrace();
			}
		}
	}
}
