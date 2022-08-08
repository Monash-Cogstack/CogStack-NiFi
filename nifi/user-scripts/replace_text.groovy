//This script is used to modify the flowfile contents to remove/replace any specified value. 
//The script was used to remove certain Unicode characters that caused Medcat processor failure. 

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.commons.io.IOUtils
import org.apache.nifi.processor.io.StreamCallback
import java.nio.charset.StandardCharsets

// NiFi flow: get the input flow file
def flowFile = session.get();
if (!flowFile) {
    return;
}

flowFile = session.write(flowFile, { inputStream, outputStream ->

  // read the input json
  def flowFileContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8)
  def inJson = new JsonSlurper().parseText(flowFileContent)

  // process each record
  def outContent = []

  inJson.content.each { rec ->
    outRec = [:]
    rec.each {k, v ->
      //condition to remove \u00a0 from text
      if (k.equals("text"))
            v=v.replaceAll("\\\u00a0", " ") //replacing the Unicode character with a blank space
      outRec.put(k,v)
    }
    outContent.add(outRec)
  }

  // prepare and store the output JSON in the Flow file
  outJson = [:]
  outJson.content  = outContent
  outputStream.write(JsonOutput.toJson(outJson).toString().getBytes(StandardCharsets.UTF_8))
} as StreamCallback)


// NiFi: transfer the seesions Flow file
session.transfer(flowFile, REL_SUCCESS)



