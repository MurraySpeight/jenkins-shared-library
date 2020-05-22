#!/usr/bin/env groovy

import digital.speight.NexusUtils

def initClass() {
    return new NexusUtils(this)
}

def downloadArtefactCurl(String appShortName, String version) {
    def utils = initClass()
    utils.downloadArtefactCurl(appShortName, version)
}
