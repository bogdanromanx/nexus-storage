String version = env.BRANCH_NAME
Boolean isRelease = version ==~ /v\d+\.\d+\.\d+.*/
Boolean isPR = env.CHANGE_ID != null

pipeline {
    agent { label 'slave-sbt' }
    options {
        timeout(time: 30, unit: 'MINUTES') 
    }
    stages {
        stage("Review") {
            when {
                expression { isPR }
            }
            parallel {
                stage("Static Analysis") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean scalafmtCheck scalafmtSbtCheck test:scalafmtCheck compile test:compile scapegoat'
                        }
                    }
                }
                stage("Tests & Coverage") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh "sbt clean coverage test coverageReport coverageAggregate"
                            sh "curl -s https://codecov.io/bash >> ./coverage.sh"
                            sh "bash ./coverage.sh -t `oc get secrets codecov-secret --template='{{.data.nexus_storage}}' | base64 -d`"
                        }
                    }
                }
            }
        }
        stage("Build & Publish Artifacts") {
            when {
                expression { !isPR }
            }
            steps {
                checkout scm
                sh 'sbt releaseEarly universal:packageZipTarball'
                stash name: "service", includes: "target/universal/storage-*.tgz"
            }
        }
        stage("Build Image") {
            when {
                expression { !isPR }
            }
            steps {
                unstash name: "service"
                sh "mv target/universal/storage-*.tgz ./storage.tgz"
                sh "oc start-build storage-build --from-file=storage.tgz --wait"
            }
        }
        stage("Redeploy & Test") {
            when {
                expression { !isPR && !isRelease }
            }
            steps {
                sh "oc scale statefulset storage --replicas=0 --namespace=bbp-nexus-dev"
                sh "oc wait pods/storage-0 --for=delete --namespace=bbp-nexus-dev --timeout=1m"
                sh "oc scale statefulset storage --replicas=1 --namespace=bbp-nexus-dev"
                sh "oc wait pods/storage-0 --for condition=ready --namespace=bbp-nexus-dev --timeout=4m"
                build job: 'nexus/nexus-tests/master', parameters: [booleanParam(name: 'run', value: true)], wait: true
            }
        }
        stage("Tag Images") {
            when {
                expression { isRelease }
            }
            steps {
                openshiftTag srcStream: 'storage', srcTag: 'latest', destStream: 'storage', destTag: version.substring(1), verbose: 'false'
            }
        }
        stage("Push to Docker Hub") {
            when {
                expression { isRelease }
            }
            steps {
                unstash name: "service"
                sh "mv target/universal/storage-*.tgz ./storage.tgz"
                sh "oc start-build nexus-storage-build --from-file=storage.tgz --wait"
            }
        }
        stage("Report Coverage") {
            when {
                expression { !isPR }
            }
            steps {
                checkout scm
                sh "sbt clean coverage test coverageReport coverageAggregate"
                sh "curl -s https://codecov.io/bash >> ./coverage.sh"
                sh "bash ./coverage.sh -t `oc get secrets codecov-secret --template='{{.data.nexus_storage}}' | base64 -d`"
            }
            post {
                always {
                    junit 'target/test-reports/TEST*.xml'
                }
            }
        }
    }
}