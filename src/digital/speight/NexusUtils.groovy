package digital.speight

class NexusUtils implements Serializable {

    private script

    NexusUtils(script) {
        this.script = script
    }

    def downloadArtefactCurl(appShortName, version) {
        def GROUP_ID = script.sh(script: 'mvn -q -Dexec.executable=echo -Dexec.args="\\${project.groupId}" --non-recursive exec:exec 2>/dev/null', returnStdout: true).trim()
        def REPO = 'release-repo'
        def NEXUS_URI = 'https://speight.digital/nexus/service/local/artifact/maven/redirect'
        script.withCredentials([usernameColonPassword(credentialsId: 'nexus', variable: 'USER')]) {
            return "curl -u $USER -L '$NEXUS_URI?r=$REPO&g=$GROUP_ID&a=api&v=$version' -o ${appShortName}.jar"
        }
    }

}
