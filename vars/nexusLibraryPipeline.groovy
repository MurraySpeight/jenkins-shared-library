def call(Map pipelineParams) {
    pipeline {

        agent { label 'Linux' }

        tools {
            maven 'apache-maven-3.5.4'
            jdk 'Java8'
        }

        environment {
            MAVEN_SETTINGS = credentials('maven-settings')
        }

        parameters {
            string(name: 'NEW_TAG', defaultValue: '', description: 'New tag')
        }

        stages {

            stage('Build') {
                steps {
                    script {
                        NEW_TAG = params.NEW_TAG.isEmpty() ? versionUtils.patchVersionIncrementer() : params.NEW_TAG
                    }
                    sh "mvn -B clean verify -s $MAVEN_SETTINGS"
                }
            }

            stage('SonarQube scan') {
                steps {
                    script {
                        SQ_BRANCH = env.BRANCH_NAME != 'master' ? "-Dsonar.branch.name=${env.BRANCH_NAME}" : ''
                    }
                    withSonarQubeEnv(installationName: 'SonarQube', credentialsId: 'sonarqube') {
                        sh "mvn sonar:sonar $SQ_BRANCH -Dsonar.projectVersion=$NEW_TAG -s $MAVEN_SETTINGS"
                    }
                }
            }

            stage('Snapshot to Nexus') {
                when {
                    not {
                        branch 'master'
                    }
                }
                steps {
                    echo "Uploading version ${NEW_TAG}-SNAPSHOT to Nexus"
                    sh "mvn -B deploy -Drevision=$NEW_TAG -Dchangelist=-SNAPSHOT -DskipTests -s $MAVEN_SETTINGS"
                }
            }

            stage('Release to Nexus') {
                when {
                    branch 'master'
                }
                steps {
                    echo "Creating tag $NEW_TAG"
                    sh 'git config --local credential.helper "!p() { echo username=\\$GIT_USERNAME; echo password=\\$GIT_PASSWORD; }; p"'
                    sh "git tag -m \"CI tagging\" $NEW_TAG"
                    echo "Uploading version $NEW_TAG to Nexus"
                    sshagent(['ssh-user']) {
                        sh "git push origin $NEW_TAG --repo=$GIT_URL"
                    }
                    sh "mvn -B deploy -Drevision=$NEW_TAG -Dchangelist= -DskipTests -s $MAVEN_SETTINGS"
                }
            }

        }
    }

}