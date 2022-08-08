@Grab('org.apache.avro:avro:1.8.1')
import org.apache.avro.*
import org.apache.avro.file.*
import org.apache.avro.generic.*
import java.nio.ByteBuffer

// needs docfield, sequencenumfield and eventidfield in processor


import static Constants.*

// This script takes a collection of records belonging to the same eventid and
// concatenates the blobs. The result is placed in the first record. Other
// records are discared.
//
class Constants {
    static final int sufflen = 9
}

def flowFile = session.get()
if(!flowFile) return

try {

    flowFile = session.write(flowFile, {inStream, outStream ->
	// Defining avro reader and writer
	DataFileStream<GenericRecord> reader = new DataFileStream<>(inStream, new GenericDatumReader<GenericRecord>())
	DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<GenericRecord>())

	// get avro schema
	def schema = reader.schema

	// counters
	int Index = 0
	GenericRecord pattern
	def bloblist = []
	def seqlist = []
	def eventlist = []
	while (reader.hasNext()) {
	    GenericRecord currRecord = reader.next()
	    if (Index == 0) {
		// save a copy of the first record for later
		pattern = currRecord
	    }
	    seqlist.add(currRecord.get(sequencenumfield as String))
	    eventlist.add(currRecord.get(eventidfield as String))

	    ByteBuffer sblob = currRecord.get(docfield as String)
	    bloblist.add(sblob)
	    ++Index;
	}
	// sanity checks on ordering etc
	def expectedSeq = 1..Index
	if (expectedSeq != seqlist) {
	    throw new Exception('Error sequence numbers not as expected.')
	}
	if (eventlist.count(eventlist[0]) != eventlist.size() ) {
	    throw new Exception('Flowfile contains multiple event IDs')
	}
	// strip the suffix bytes of all but the last one
	for (idx = 0; idx < bloblist.size() - 1; idx++) {
	    
	    int origsize = bloblist[idx].remaining();
	    ByteBuffer newblob = ByteBuffer.allocate(origsize - sufflen)
	    newblob.put(bloblist[idx].array(), 0, origsize - 9)
	    newblob.flip()
	    bloblist[idx] = newblob
	}
	// figure out the total length
	sizes = bloblist.collect{it.remaining()}
	total = sizes*.value.sum()
	finalblob = ByteBuffer.allocate(total)
	for (idx = 0; idx < bloblist.size(); idx++) {
	    finalblob.put(bloblist[idx].array(), 0, bloblist[idx].remaining())
	}
	finalblob.flip()
	pattern.put(docfield as String, finalblob)
	// Define which schema to be used for writing
	// If you want to extend or change the output record format
	// you define a new schema and specify that it shall be used for writing
	writer.create(schema, outStream)
	writer.append(pattern)
	writer.close()

    } as StreamCallback)

    session.transfer(flowFile, REL_SUCCESS)
} catch(e) {
    log.error('Error appending new record to avro file', e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
