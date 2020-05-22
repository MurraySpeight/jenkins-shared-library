def call(Map pipelineParams) {
    pipeline {

        agent { label 'Linux' }

        tools {
            maven 'apache-maven-3.5.4'
            jdk 'Java8'
        }

        environment {
            MAVEN_SETTINGS = credentials('maven-settings')
            POWER_USER = 'username'
            SIT_HOSTNAME = 'hostname'
            APP_SHORT_NAME = "${pipelineParams.appName}"
            APP_DEPLOY_DIR = "/apps/$APP_SHORT_NAME"
            APP_SERVICE_NAME = "$APP_SHORT_NAME"
        }

        parameters {
            string(name: 'NEW_TAG', defaultValue: '', description: 'New tag')
        }

        stages {

            stage('Build and deploy snapshot') {
                when {
                    not {
                        buildingTag()
                    }
                }
                stages {

                    stage('Install npm') {
                        steps {
                            sh '''
                                source "$NVM_DIR/nvm.sh"
                                nvm install
                            '''
                        }
                    }

                    stage('Build') {
                        steps {
                            script {
                                NEW_TAG = params.NEW_TAG.isEmpty() ? versionUtils.patchVersionIncrementer() : params.NEW_TAG
                            }
                            sh "mvn -B clean verify -s $MAVEN_SETTINGS"
                            dir('api') {
                                retry(3) {
                                    sh "mvn -B com.google.cloud.tools:jib-maven-plugin:build -Djib.to.image.tags=${versionUtils.dockerTag()} -s $MAVEN_SETTINGS"
                                }
                            }
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
                            script {
                                NEW_TAG = params.NEW_TAG.isEmpty() ? versionUtils.patchVersionIncrementer() : params.NEW_TAG
                            }
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

                    stage('Deploy snapshot to SIT') {
                        when {
                            not {
                                branch 'master'
                            }
                        }
                        steps {
                            script {
                                VERSION = NEW_TAG + '-SNAPSHOT'
                            }
                            withCredentials([sshUserPrivateKey(credentialsId: 'swebbwli-ssh', keyFileVariable: 'SSH_KEY')]) {
                                sh """\
                                cat $SSH_KEY > sshkey
                                chmod 600 sshkey
                                eval `ssh-agent -s`
                                ssh-add sshkey
                                scp -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no api/target/api-${VERSION}.jar $POWER_USER@$SIT_HOSTNAME:$APP_DEPLOY_DIR/${APP_SHORT_NAME}.jar
                                ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -l $POWER_USER $SIT_HOSTNAME <<EOF
                                cd $APP_DEPLOY_DIR
                                echo "TODO service $APP_SERVICE_NAME stop"
                                rm -rf $VERSION                 
                                mkdir $VERSION
                                mv ${APP_SHORT_NAME}.jar $VERSION/${APP_SHORT_NAME}.jar
                                chmod +x $VERSION/${APP_SHORT_NAME}.jar
                                mkdir -p process
                                echo PID_FOLDER=/apps/$APP_SHORT_NAME/process > $VERSION/${APP_SHORT_NAME}.conf
                                echo LOG_FOLDER=/apps/$APP_SHORT_NAME/process >> $VERSION/${APP_SHORT_NAME}.conf
                                echo RUN_ARGS=\\"--spring.profiles.active=dev --spring.config.additional-location=file:///apps/current/env.properties\\" >> $VERSION/${APP_SHORT_NAME}.conf
                                ln -nvfs $VERSION current
                                echo "TODO service $APP_SERVICE_NAME start"
                                EOF
                            """.stripIndent()
                            }
                        }
                    }

                }
            }

            stage("Deploy release") {
                when {
                    buildingTag()
                }
                stages {

                    stage('Deploy release to SIT') {
                        steps {
                            script {
                                LATEST_TAG = versionUtils.latestTag()
                            }
                            withCredentials([sshUserPrivateKey(credentialsId: 'swebbwli-ssh', keyFileVariable: 'SSH_KEY')]) {
                                sh """\
                                cat $SSH_KEY > sshkey
                                chmod 600 sshkey
                                eval `ssh-agent -s`
                                ssh-add sshkey
                                ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -l $POWER_USER $SIT_HOSTNAME <<EOF
                                cd $APP_DEPLOY_DIR
                                mkdir -p process
                                mkdir $LATEST_TAG
                                cd $LATEST_TAG
                                echo PID_FOLDER=/apps/$APP_SHORT_NAME/process > ${APP_SHORT_NAME}.conf
                                echo LOG_FOLDER=/apps/$APP_SHORT_NAME/process >> ${APP_SHORT_NAME}.conf
                                echo RUN_ARGS=\\"--spring.profiles.active=dev --spring.config.additional-location=file:///apps/current/env.properties\\" >> ${APP_SHORT_NAME}.conf                                
                                ${nexusUtils.downloadArtefactCurl(APP_SHORT_NAME,LATEST_TAG)}
                                chmod +x ${APP_SHORT_NAME}.jar
                                cd ..
                                echo "TODO service $APP_SERVICE_NAME stop"
                                ln -nvfs $LATEST_TAG current
                                echo "TODO service $APP_SERVICE_NAME start"
                                EOF
                            """.stripIndent()
                            }
                        }
                    }

                    stage('Deploy release to UAT') {
                        steps {
                            // TODO
                            echo 'Deploy to UAT'
                        }
                    }

                    stage('Deploy to production') {
                        steps {
                            // TODO
                            echo 'Deploy to production'
                        }
                    }

                }
            }

        }

    }

}