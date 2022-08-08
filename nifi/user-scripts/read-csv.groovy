//This script is to read data from a csv file and add the required values to the flowfile content 

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
  def value
  def headers = []

// dataFile is the CSV file from which the data it to be read
  File dataFile = new File(filePath as String)

  inJson.hits.each { rec ->
    outRec = [:]
    rec.each {k, v ->
      if (k.equals(search_key as String)) 
//search_key is the key whose corresponding value will be used for lookup in the dataFile. eg. search_key = ID. The ID value will be used to find matching data
            search_value = v
      outRec.put(k,v)
    }
    dataFile.readLines().eachWithIndex { row, rowIndex ->
      if (rowIndex == 0) { headers = row.split(',') }
      else {
        def cells = row.split(',')
        cells.eachWithIndex { cell, cellIndex ->
          if (headers[cellIndex] == search_column as String && cell == search_value) //search_column is the columnd in the dataFile that is being matched with the search_key value
          {
            requiredIndex = headers.findIndexOf{it == display_column as String}
            value = cells[requiredIndex]//the value from the dataFile to be appended to the flowfile content
          }
        }
      }
    }  
    outRec.put(display_column,value)
    outContent.add(outRec)
  }

  // prepare and store the output JSON in the Flow file
  outJson = [:]
  outJson.hits  = outContent
  outputStream.write(JsonOutput.toJson(outJson).toString().getBytes(StandardCharsets.UTF_8))
} as StreamCallback)


// NiFi: transfer the seesions Flow file
session.transfer(flowFile, REL_SUCCESS)


