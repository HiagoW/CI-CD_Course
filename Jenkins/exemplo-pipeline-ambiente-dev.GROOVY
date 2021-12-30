pipeline {
    // Imagem passada como parametro no job e setada como variavel de ambiente 
    environment {
        dockerImage = "${image}"
    }
    agent any

    stages {
        stage('Carregando o ENV de desenvolvimento') {
            steps {
                // ID do arquivo .env-dev
                configFileProvider([configFile(fileId: 'f6edc037-169a-489d-ac35-7abdd1172f3d', variable: 'env')]) {
                    sh 'cat $env > .env'
                }
            }
        }
        stage('Derrubando o container antigo') {
            steps {
                script {
                    try {
                        sh 'docker rm -f django-todolist-dev'
                    } catch (Exception e) {
                        sh "echo $e"
                    }
                }
            }
        }        
        stage('Subindo o container novo') {
            steps {
                script {
                    try {
                        // Cria voluma com arquivo .env
                        sh 'docker run -d -p 81:8000 -v /var/run/mysqld/mysqld.sock:/var/run/mysqld/mysqld.sock -v /var/lib/jenkins/workspace/todo-list-desenvolvimento/.env:/usr/src/app/to_do/.env --name=django-todolist-dev ' + dockerImage + ':latest'
                    } catch (Exception e) {
                        //Notificação Slack. slack-token é o nome do sercret text criado nas configurações do jenkins
                        slackSend (color: 'error', message: "[ FALHA ] Não foi possivel subir o container - ${BUILD_URL} em ${currentBuild.duration}s", tokenCredentialId: 'slack-token')
                        sh "echo $e"
                        currentBuild.result = 'ABORTED'
                        error('Erro')
                    }
                }
            }
        }
        stage('Notificando o usuario') {
            steps {
                slackSend (color: 'good', message: '[ Sucesso ] O novo build esta disponivel em: http://192.168.33.10:81/ ', tokenCredentialId: 'slack-token')
            }
        }
        stage ('Fazer o deploy em producao?') {
            steps {
                script {
                    slackSend (color: 'warning', message: "Para aplicar a mudança em produção, acesse [Janela de 10 minutos]: ${JOB_URL}", tokenCredentialId: 'slack-token')
                    timeout(time: 10, unit: 'MINUTES') {
                        // Se clicar no botão "Deploy", continua o Job. Se não, aborta
                        input(id: "Deploy Gate", message: "Deploy em produção?", ok: 'Deploy')
                    }
                }
            }
        }
        stage (deploy) {
            steps {
                script {
                    try {
                        // Executa job todo-list-producao, passando parametro image com nome da imagem no docker hub
                        build job: 'todo-list-producao', parameters: [[$class: 'StringParameterValue', name: 'image', value: dockerImage]]
                    } catch (Exception e) {
                        slackSend (color: 'error', message: "[ FALHA ] Não foi possivel subir o container em producao - ${BUILD_URL}", tokenCredentialId: 'slack-token')
                        sh "echo $e"
                        currentBuild.result = 'ABORTED'
                        error('Erro')
                    }
                }
            }
        }
    }
}