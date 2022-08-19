# Peninsula Health Changes
This document covers the work that’s been done for Peninsula Health under the MRFF grant and by the technical team at Monash University led by Helix Platform. It includes the Cogstack deployment and pipeline development on Peninsula Health server 

| Abbreviation | Meaning |
| ---- | ----|
| PH | Peninsula Health      |
| ES | Elastic Search        |

## Content

1.	ElasticSearch Basics
2.	Nifi Setup
3.	New CogStack Functionality

## ElasticSearch Basics

The official ElasticSearch documentation can be found [here](https://www.elastic.co/guide/en/elasticsearch/reference/master/index.html).

The indices that are used in the workflows can be viewed by [creating an index pattern](https://www.elastic.co/guide/en/kibana/7.17/index-patterns.html).  Once this is done, the data within the index can be viewed through the Kibana interface. 
Data searches are to be done through the [Kibana Query Language (KQL)](https://www.elastic.co/guide/en/kibana/current/kuery-query.html).
More advanced queries can be run through the Dev Tools console [accessible through the side menu > Management > Dev Tools]. 
NOTE: By default, ElasticSearch only returns 10 results for each query. If more than 10 are needed, it has to be specified using the **size** parameter. 

Example queries are listed below:
1. Deleting an Index

        DELETE /<index_name>

        Eg. 
        DELETE /index_test

2. Get all data from an index

        GET /<index_name>/_search
        {
            "query": {
                "match_all": {}
            }
        }

        Eg. 
        GET /test_data/_search
        {
            "query": {
                "match_all": {}
            }
        }

3. Returning a list of unique values from an index

        GET /<index_name>/_search
        {
            "size": 0,
            "aggs":
            {
                "distinct_<field_name>":
                {
                    "terms":
                    {
                        "field": "<field_name>",
                        "size": <required_number_of_results>
                    }
                }
            }
        }

        Eg. 
        GET /test_logs/_search
        {
            "size": 0,
            "aggs":
            {
                "distinct_footer.doc_id":
                {
                    "terms":
                    {
                        "field": "footer.doc_id",
                        "size": 1000
                    }
                }
            }
        }

## Nifi Setup
To run a workflow in Apache Nifi, follow the following steps: 
1. Select a new processor from the menu on top to build your workflow.  
OR  
Select a pre-existing template from the available list. 
2. If any of the processors display the yellow alert symbol, hover over it to see the issue.  
**Common Issues:**  
    *  A service being disabled: Open the Processor properties and select the Go To arrow to move to the screen where the service can be enabled.  
    * Password missing [Eg. for access to DB]: Open the Processor properties and provide the password.  
    * Required files missing/Incorrect file paths: If the processor utilises any files located in the nifi container, ensure the file exists and the path to the file is correct. 
3. Once the processor is configured correctly, it should have a Stop symbol in place of the alert symbol. Right click and select Start to start the processor. 

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
 



