#!/usr/bin/env groovy

import digital.speight.VersionUtils

def initClass() {
    return new VersionUtils(this)
}

def patchVersionIncrementer() {
    def utils = initClass()
    utils.patchVersionIncrementer()
}

def dockerTag() {
    def utils = initClass()
    utils.dockerTag()
}

def latestTag() {
    def utils = initClass()
    utils.latestTag()
}