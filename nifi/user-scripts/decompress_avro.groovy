@Grab('org.apache.avro:avro:1.8.1')
import org.apache.avro.*
import org.apache.avro.file.*
import org.apache.avro.generic.*
import java.nio.ByteBuffer
import DecompressOcf.DecompressBlob

// from https://github.com/maxbback/avro_reader_writer/blob/master/avroProcessor.groovy


// needs docfield and compressfield in the host processor

def flowFile = session.get()
if(!flowFile) return

try {

    flowFile = session.write(flowFile, {inStream, outStream ->
	// Defining avro reader and writer
	DataFileStream<GenericRecord> reader = new DataFileStream<>(inStream, new GenericDatumReader<GenericRecord>())
	DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<GenericRecord>())

	// get avro schema
	def schema = reader.schema
	// in my case I am processing a address lookup table data with only one field the address field
	// {"type":"record","name":"lookuptable","namespace":"any.data","fields":[{"name":"address","type":["null","string"]}]}

	// Define which schema to be used for writing
	// If you want to extend or change the output record format
	// you define a new schema and specify that it shall be used for writing
	writer.create(schema, outStream)
	// process record by record
	while (reader.hasNext()) {
	    GenericRecord currRecord = reader.next()
	    int CompressionCode = currRecord.get(compressfield as String)
	    // log.warn(String.format("%f", len))
	    ByteBuffer sblob = currRecord.get(docfield as String)
	    if (CompressionCode == 728) {
		// This blob is compressed - create the decompressor
 		DecompressOcf.DecompressBlob DC = new DecompressOcf.DecompressBlob()
		DC.Test()
		byte[] newblob = DC.DecompressBB(sblob)
		currRecord.put(docfield as String, ByteBuffer.wrap(newblob))
	    } else if (CompressionCode == 727) {
		// this blob isn't compressed - strip the suffix
		int sufflen = 9
		int origsize = sblob.remaining();
		ByteBuffer newblob = ByteBuffer.allocate(origsize - sufflen)
		newblob.put(sblob.array(), 0, origsize - 9)
		newblob.flip()
		currRecord.put(docfield as String, newblob)
	    } else {
		log.error('Unknown compression code')
	    }
	    writer.append(currRecord)
	}
	// Create a new record
	//   GenericRecord newRecord = new GenericData.Record(schema)
	// populate the record with data
	//   newRecord.put("address", new org.apache.avro.util.Utf8("My street"))
	// Append a new record to avro file
	//   writer.append(newRecord)
	//writer.appendAllFrom(reader, false)
	// do not forget to close the writer
	writer.close()

    } as StreamCallback)

    session.transfer(flowFile, REL_SUCCESS)
} catch(e) {
    log.error('Error appending new record to avro file', e)
    flowFile = session.penalize(flowFile)
    session.transfer(flowFile, REL_FAILURE)
}
