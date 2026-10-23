param([int]$Port = 8081)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
mvn "-Dmaven.repo.local=$PWD\.maven-repo" test-compile dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt' '-Dmdep.includeScope=test' -q
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
$collabClasspath = 'target\test-classes;target\classes;' + (Get-Content -LiteralPath target\classpath.txt -Raw).Trim()
java -cp $collabClasspath com.collabhub.DevServer $Port
