# Peninsula Health Changes
This document covers the work that’s been done for Peninsula Health under the MRFF grant and by the technical team at Monash University led by Helix Platform. It includes the Cogstack deployment and pipeline development on Peninsula Health server 

| Abbreviation | Meaning |
| ---- | ----|
| PH | Peninsula Health      |
| ES | Elastic Search        |

## Content

1.	ElasticSearch Basics
2.	Nifi Setup
3.	Cogstack Pipeline

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

## CogStack Pipeline
The CogStack code is stored on the Monash GitHub repo [here.](https://github.com/Monash-Cogstack/CogStack-NiFi)

The following changes have been made to the CogStack code, in line with the work done at Peninsula Health:   
1. Updated the codebase to include the code changes to address and fix the log4j vulnerability.
2. Updated groovy scripts -  `parse-es-result-for-nlp-request-bulk.groovy` and `parse-db-records-for-nlp-request-bulk.groovy` to include the processing time for easier analysis.
3. Created a new workflow for the combined annotation of dementia and non-dementia patients: `Combined_annotation_dementia` under `CogStack-NiFi/nifi/user-templates`.
This workflow also includes the addition of new features-  
    * Created a new groovy script to read data from a csv file and append this data into the flowfiles during processing. This was used during the dementia/non-dementia annotations to map the person_id to their Cohort mapping.  
    Script: `CogStack-NiFi/nifi/user-scripts/read-csv.groovy`
    * Developed new workflows for logging the files that enter the retry and failure queue. These files are written into Elasticsearch indices for easy access and to view stats such as the number of unique IDs in these files.  
    The logging workflow will split the incoming flowfile into its individual documents, a particular value (like the doc_id) can be extracted if needed and then the file is written into Elasticsearch.
    * Developed a new script that will replace characters in the flowfiles during processing. This does not impact the original content of the file. This script is used in the combined dementia annotation workflow to remove a certain character that was identified during testing which was breaking the annotation process and resulted in files looping through the retry queue several times before eventually going to failure.  
    Script: `CogStack-NiFi/nifi/user-scripts/replace_text.groovy`
    * A testing script was used to identify the document IDs that were not annotated. `CogStack-NiFi/testing/find_missing_ids.r`. This script will compare the IDs in the target ES index and in the source CSV file and generate a list of IDs not present in the ES index. 
4. Created a workflow to annotate the entire dataset: `Annotate_All_Docs` under `CogStack-NiFi/nifi/user-templates`. 
Features of this workflow:  
    * Logging functionality has been added: files from the retry/failure queues are written to an ES index.
    * An extra processor (Replace text) has also been added to remove the No Break Space unicode character, because of which files were stuck indefinitely in the medcat retry queue.
    * This workflow also implements an `annotation_status` flag which is used to keep track of the documents successfully annotated, and a `medcat_processing_time` attribute to track the time the file has spent in the medcat processor.  The workflow has been modified to only allow one file at a time to enter the Medcat processor to get accurate timing for processing (otherwise processing time will include the time spent waiting in the queue). 
5. There are also scripts and workflows added in to ingest data from SQL to an ES index, and to decompress data blobs. 
 



