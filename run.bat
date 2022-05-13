@echo off
setlocal
cd /d %~dp0

set DEBUG_OPTS=

REM set BatchPath=%~dp0
REM set JENKINS_HOME=%BatchPath%work
REM "c:\program files\java\jdk1.8.0_131\jre\bin\java" -jar .\jenkins.war --httpPort=8080 -Dstapler.trace=false -Dstapler.jelly.noCache=false -Ddebug.YUI=false


if "%1"=="" goto RUN
set DEBUG_OPTS=-Dorg.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005


:RUN
set OPTS=%DEBUG_OPTS% -Xmx1024m -Xms1024m

echo run options = %OPTS%
start .\gradlew server -Dstapler.trace=false -Dstapler.jelly.noCache=false -Ddebug.YUI=false "%OPTS%" 
