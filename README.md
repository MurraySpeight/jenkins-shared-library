# Jenkins Pipeline Shared Library

#### Spring Boot Web Pipeline
This pipeline performs
* build
* test
* SonarQube scan
* deploy artefact to Nexus
* deploy from Nexus to SIT environment

It can be called in a Jenkinsfile with the below code, using the Maven artifact ID as the appName:
```
@Library('jenkins-library')_
spingBootWebPipeline(appName: 'app')
```