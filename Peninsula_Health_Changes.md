# Peninsula Health Changes
This document covers the work that’s been done for Peninsula Health under the MRFF grant and by the technical team at Monash University led by Helix Platform. It includes the Cogstack deployment and pipeline development on Peninsula Health server 

| Abbreviation | Meaning |
| ---- | ----|
| PH | Peninsula Health      |
| ES | Elastic Search        |

## New CogStack Functionality
The CogStack code is stored on the Monash GitHub repo [here.](https://github.com/Monash-Cogstack/CogStack-NiFi)

The following funtionality has been added to the CogStack code:   

### Tracking of Document Processing Time
The groovy scripts -  `parse-es-result-for-nlp-request-bulk.groovy` and `parse-db-records-for-nlp-request-bulk.groovy` have bene updated to include the processing time for easy analysis.  
A new data attribute, `medcat_processing_time` has also been introduced in the workflow to track the time the file has spent in the medcat processor. The processing time can then be written back into an ES index.  

This feature can be seen implemented in the workflow `Annotate_All_Docs` under `CogStack-NiFi/nifi/user-templates`.

### Addition of New Data Attributes During Processing
While processing documents, the workflow can be configured to read data from a CSV file and append the values to the flowfiles. This ensures the resulting flowfile has all the required data, while the source index remains untouched.   
* Created a new groovy script, `CogStack-NiFi/nifi/user-scripts/read-csv.groovy`, that read data from a csv file and appends it into the flowfiles.  
This was used during the dementia/non-dementia annotations to map the patient's ID to their Cohort mapping.  

This feature can be seen implemented in the combined dementia annotation workflow `Combined_annotation_dementia` under `CogStack-NiFi/nifi/user-templates`.

### Logging
New workflows for logging have been built. These will keep track of the files that enter the Retry or Failure queues [and can be configured to log the files entering any other queue as well].  
These files are written into ElasticSearch indices for easy access and to view stats, such as the number of unique IDs in these files.  
The logging workflow will split the incoming flowfile into its individual documents, a particular value (like the document ID) can be extracted if needed and then the file is written into Elasticsearch.

This feature can be seen implemented in the combined dementia annotation workflow `Combined_annotation_dementia` under `CogStack-NiFi/nifi/user-templates`.  
It is also used in the workflow `Annotate_All_Docs` under `CogStack-NiFi/nifi/user-templates`.

### Replacing Text in a Flowfile During Processing
A script `CogStack-NiFi/nifi/user-scripts/replace_text.groovy` was developed that can be used to replace characters in the flowfiles during processing. The original file in the source index will remain unchanged.  
This script was used to remove a certain character that was identified during testing which was breaking the annotation process and resulted in files looping through the retry queue several times before eventually going to failure.  

This feature can be seen implemented in the combined dementia annotation workflow `Combined_annotation_dementia` under `CogStack-NiFi/nifi/user-templates`.  
It is also used in the workflow `Annotate_All_Docs` under `CogStack-NiFi/nifi/user-templates`.

### Identification of Documents Not Processed
A testing script, `CogStack-NiFi/testing/find_missing_ids.r`, was written which can be used to compare columns in two different data collections.  
It was used to identify the document IDs that were not annotated by comparing the IDs in the target ES index and in the source CSV file/source ES index and generating a list of IDs not present in the target ES index.  
The script can also be used to identify the files that have not been ingested into ElasticSearch, by comparing the target ES index with the source index or even with an SQL table. 

### Restarting an Interrupted Workflow 
Previously, when a workflow was intterrupted (due to system failures etc), its processing was started again from scratch on restarting the workflow. For the annotation workflows, this meant that all documents had to be annotated again from the beginning every time the workflow was interrupted.  
This has been addressed by introducing a flag, `annotation_status`, in the source index which is used to keep track of the documents successfully annotated. This flag has a default value of `false` and is set to `true` upon successful processing of the file. When restarting the workflow, the condition `annotation_status = false` is used to only read the files that have not been processed yet.  

This feature can be seen implemented in the workflow `Annotate_All_Docs` under `CogStack-NiFi/nifi/user-templates`.

### Restriction of File Movement in the Workflow
A modification has been introduced to restrict the number of files that can enter a section of the workflow.  
It is being used to allow only one file at a time to move through the Medcat processing section. This has been done to ensure the timestamps are accurate and do not include the time spent waiting in queue. 

This modification can be seen implemented in the workflow `Annotate_All_Docs` under `CogStack-NiFi/nifi/user-templates`.
 



