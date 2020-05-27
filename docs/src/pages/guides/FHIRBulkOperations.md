---
layout: post
title: FHIR Bulk Data Guide
description: FHIR Bulk Data Guide
date:   2020-05-08 09:00:00 -0500
permalink: /FHIRBulkOperations/
---

# Overview

The IBM FHIR Server has extended operations for Bulk Data `$import` and `$export`, which are implemented in the modules: 

|Module|Description|
|---|---|
|[fhir-operation-bulkdata](https://github.com/IBM/FHIR/tree/master/fhir-operation-bulkdata)|Implements the FHIR Operations `$import` and `$export` and translate bulk data requests into JSR352 Java Batch jobs|
|[fhir-bulkimportexport-webapp](https://github.com/IBM/FHIR/tree/master/fhir-bulkimportexport-webapp)|Standalone web application to process bulk data requests as JSR352 Java Batch jobs|

The IBM FHIR Server bulk data module configuration is described in more detail at the [FHIR Server Users Guide](https://ibm.github.io/FHIR/guides/FHIRServerUsersGuide/#410-bulk-data-operations).

## Export Operation: $export
The `$export` operation uses three OperationDefinition: 
- [System](http://hl7.org/fhir/uv/bulkdata/STU1/OperationDefinition-export.html) - Export data from the server. Exports to an S3-compatible data store.
- [Patient](http://hl7.org/fhir/uv/bulkdata/STU1/OperationDefinition-patient-export.html) - Obtain a set of resources pertaining to all patients. Exports to an S3-compatible data store.
- [Group](http://hl7.org/fhir/uv/bulkdata/STU1/OperationDefinition-group-export.html) - Obtain a set of resources pertaining to patients in a specific Group. Only supports static membership; does not resolve inclusion/exclusion criteria.

### **$export: Create a Bulk Data Request**
To create an import request, the IBM FHIR Server requires the body fields of the request object to be a FHIR Resource `Parameters` JSON Object.  The request must be posted to the server using `POST`. Each request is limited to a single resource type in each imported or referenced file.

#### Example Request - GET
The following is a request to export data to the IBM COS endpoint from the IBM FHIR Server using GET.

```sh
curl -k -u "fhiruser:change-password" -H "Content-Type: application/fhir+json" -X GET 'https://localhost:9443/fhir-server/api/v4/$export?_outputFormat=application/fhir%2Bndjson&_type=Patient' -v
```

#### Example Request - POST
The following is a request to export data to the IBM COS endpoint from the IBM FHIR Server using POST and FHIR Resource Parameters.

```sh
curl -k -u "fhiruser:change-password" -H "Content-Type: application/fhir+json" -X POST 'https://localhost:9443/fhir-server/api/v4/$export' -d '{
    "resourceType": "Parameters",
    "id": "e33a6a4e-29b5-4f62-a3e9-8d09f50ae54d",
    "parameter": [
        {
            "name": "_outputFormat",
            "valueString": "application/fhir+ndjson"
        },
        {
            "name": "_since",
            "valueInstant": "2019-01-01T08:21:26.94-04:00"
        },
        {
            "name": "_type",
            "valueString": "Observation,Condition"
        }
    ]
}'
```

## Import Operation: $import

The `$import` operation is a system-level operation invoked at `[base]/$import`. The Import Operation uses a custom crafted OperationDefinition [link](https://github.com/IBM/FHIR/blob/master/fhir-operation-bulkdata/src/main/resources/import.json), which follows the proposal from [Smart-on-FHIR: import.md](https://github.com/smart-on-fhir/bulk-import/blob/master/import.md).

### **$import: Create a Bulk Data Request**
To create an import request, the IBM FHIR Server requires the body fields of the request object to be a FHIR Resource `Parameters` JSON Object.  The request must be posted to the server using `POST`. Each input url in the request is limited to a single resource type.

The IBM FHIR Server limits the number of inputs per each `$import` request based on `fhirServer/bulkdata/maxInputPerRequest`, which defaults to 5 input entries.

The IBM FHIR Server supports `storageDetail.type` with the value of `ibm-cos`, `https` and `aws-s3`.
To import using the $import on https, one must additionally configure the fhirServer/bulkdata/validBaseUrls. For example, if one stores bulkdata on https://test-url.ibm.com/folder1 and https://test-url.ibm.com/folder2 you must specify both baseUrls. Please refer to the [IBM FHIR Server User's Guide](https://ibm.github.io/FHIR/guides/FHIRServerUsersGuide#410-bulk-data-operations)

#### Example Request
The following is a request to load data from the IBM COS endpoint into the IBM FHIR Server.

``` sh
curl -k -v -X POST -u "fhiruser:change-password" -H 'Content-Type: application/fhir+json' 'https://localhost:9443/fhir-server/api/v4/$import' -d '{
    "resourceType": "Parameters",
    "id": "30321130-5032-49fb-be54-9b8b82b2445a",
    "parameter": [
        {
            "name": "inputFormat",
            "valueString": "application/fhir+ndjson"
        },
        {
            "name": "inputSource",
            "valueUri": "https://localhost:9443/source-fhir-server"
        },
        {
            "name": "input",
            "part": [
                {
                    "name": "type",
                    "valueString": "Patient"
                },
                {
                    "name": "url",
                    "valueUrl": "test-import.ndjson"
                }
            ]
        },
        {
            "name": "storageDetail",
            "valueString": "ibm-cos"
        }
    ]
}'
```

#### Example Response
The response body is populated only on error, the response status code is 202 and the `content-location` is populated with the polling location in a response header:

``` sh
Content-Location: https://localhost:9443/fhir-server/api/v4/$bulkdata-status?job=JKyViJ5Y0zcqQ2Uv1aBSMw%3D%3D
```

## Status Operation: $bulkdata-status
The `StatusOperation` is a BulkData Management endpoint, which uses the `Content-Location`'s http query parameter value for job to manage the Bulk Data job request.

There are two custom features - *Poll Job* and *Delete Job*.

### **$bulkdata-status: Poll Job**
The response returned is 202 if the job is queued or not yet complete.
The response returned is 200 if the job is completed.

#### Example
- Request
```sh
curl -k -v -u "fhiruser:change-password" 'https://localhost:9443/fhir-server/api/v4/$bulkdata-status?job=FvHrLGPv0oKZNyLzBnY5iA%3D%3D'
```
- Response for `$import` or `$export` not yet complete
The response code is 202.

- Response for $export upon completion
```sh
{
"transactionTime": "2020/01/20 16:53:41.160 -0500",
"request": "/$export?_type=Patient",
"requiresAccessToken": false,
"output" : [
  { "type" : "AllergyIntolerance",
      "url": "https://s3.us-south.cloud-object-storage.appdomain.cloud/fhir-bulkimexport-connectathon/6SfXzbGvYl1nTjGbf5qeqJDFNjyljiGdKxXEJb4yJn8=/AllergyIntolerance_1.ndjson",
    "count": 20},
  { "type" : "AllergyIntolerance",
      "url": "https://s3.us-south.cloud-object-storage.appdomain.cloud/fhir-bulkimexport-connectathon/6SfXzbGvYl1nTjGbf5qeqJDFNjyljiGdKxXEJb4yJn8=/AllergyIntolerance_2.ndjson",
    "count": 8},
  { "type" : "Observation",
      "url": "https://s3.us-south.cloud-object-storage.appdomain.cloud/fhir-bulkimexport-connectathon/6SfXzbGvYl1nTjGbf5qeqJDFNjyljiGdKxXEJb4yJn8=/Observation_1.ndjson",
    "count": 234},
  { "type" : "Observation",
      "url": "https://s3.us-south.cloud-object-storage.appdomain.cloud/fhir-bulkimexport-connectathon/6SfXzbGvYl1nTjGbf5qeqJDFNjyljiGdKxXEJb4yJn8=/Observation_2.ndjson",
    "count": 81}]
}
```

- Response for $import upon completion
```sh
{
    "transactionTime": "2020-04-28T13:13:01.366-04:00",
    "request": "$import",
    "requiresAccessToken": false,
    "output": [
        {
            "type": "OperationOutcome",
            "url": "test-import.ndjson_oo_success.ndjson",
            "count": 3
        }
    ],
    "error": [
        {
            "type": "OperationOutcome",
            "url": "test-import.ndjson_oo_errors.ndjson",
            "count": 0
        }
    ]
}
```

### **$bulkdata-status: Delete Job**
The Bulk Data Request is deleted using the Content-Location and executing the `DELETE` method. The deletion of a job is asynchronous - the job is stopped and subsequently deleted.  The data is not cleaned up from the destination storage location - e.g. partially imported data or partially exported data is not cleaned up. 

- Request
```sh
curl -k -v -u "fhiruser:change-password" -X DELETE 'https://localhost:9443/fhir-server/api/v4/$bulkdata-status?job=k%2Fd8cTAU%2BUeVEwqURPZ3oA%3D%3D'
```

- Response
The response returned is 202 if the job deletion is not yet complete.
The response returned is 200 if the job deletion is completed.

## Notes
1. For status codes, if there is an error on the server a 500 is returned, or if there is a client request error, a 400 is returned. 
1. There are integration tests which exercise the various features of the Bulk Data Operations - `ImportOperationTest` and `ExportOperationTest`.  These integration tests are useful for testing the IBM FHIR Server, and may be useful for developers wanting to build their own tests. 
1. Depending on the access policy of your export location, one may download the content using a command like `curl -o Patient_1.ndjson https://s3.us-south.cloud-object-storage.appdomain.cloud/fhir-r4-connectathon/path-path/Patient_1.ndjson`.
1. The use of Basic Authentication `fhiruser:change-password` is expected to be changed to match your environment authentication routine.
