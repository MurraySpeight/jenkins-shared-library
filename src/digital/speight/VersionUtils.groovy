package digital.speight

class VersionUtils implements Serializable {

    private script

    VersionUtils(script) {
        this.script = script
    }

    def patchVersionIncrementer() {
        patchVersionIncrementer(latestTag())
    }

    def patchVersionIncrementer(tag) {
        final int PATCH_INDEX = 2
        def version = tag.split(/\./)
        if (version.length > PATCH_INDEX) {
            def patchNumber = version[PATCH_INDEX].find(/\d+/) as Integer
            def newPatchNumber = ++patchNumber
            version[PATCH_INDEX] = version[PATCH_INDEX].replaceFirst(/\d*/, newPatchNumber as String)
            version.join('.')
        } else {
            script.error('Version does not contain a patch version')
        }
    }

    def dockerTag() {
        "${latestTag()}-${script.env.GIT_COMMIT.substring(0, 11)}-$script.env.BUILD_NUMBER"
    }

    def latestTag() {
        script.sh(script: 'git for-each-ref --sort=taggerdate --format "%(tag)" refs/tags|tail -1', returnStdout: true).trim()
    }

}
