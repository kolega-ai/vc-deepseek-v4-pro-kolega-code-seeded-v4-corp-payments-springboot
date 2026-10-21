pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        // Test-only credentials; never production values.
        SPRING_PROFILES_ACTIVE = 'ci'
        CI_DB_URL = 'jdbc:h2:mem:ci;MODE=PostgreSQL;DB_CLOSE_DELAY=-1'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './gradlew --no-daemon clean build -x test'
            }
        }

        stage('Unit & integration tests') {
            steps {
                sh './gradlew --no-daemon test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml'
                }
            }
        }

        stage('Assemble artifacts') {
            steps {
                sh './gradlew --no-daemon bootJar'
                archiveArtifacts artifacts: '**/build/libs/*.jar', fingerprint: true, allowEmptyArchive: true
            }
        }

        stage('Report') {
            steps {
                publishHTML target: [
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'build/reports',
                    reportFiles: 'index.html',
                    reportName: 'Build Reports'
                ]
            }
        }
    }

    post {
        always {
            cleanWs(cleanWhenAborted: true, cleanWhenFailure: true, cleanWhenNotBuilt: true, cleanWhenSuccess: false)
        }
    }
}
